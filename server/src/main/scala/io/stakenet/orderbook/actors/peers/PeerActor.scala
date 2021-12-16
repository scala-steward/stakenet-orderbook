package io.stakenet.orderbook.actors.peers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import io.stakenet.orderbook.actors.connection
import io.stakenet.orderbook.actors.connection.ConnectionManagerActor
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.orders.{OrderManagerActor, PeerOrder}
import io.stakenet.orderbook.actors.peers.handlers.CommandHandler.Result
import io.stakenet.orderbook.actors.peers.handlers._
import io.stakenet.orderbook.actors.peers.protocol._
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.config.{ChannelRentalConfig, FeatureFlags, TradingPairsConfig}
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.models.trading.{Trade, TradingOrder, TradingPair}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.validators.OrderValidator
import io.stakenet.orderbook.services.{ChannelService, ClientService, FeeService, MakerPaymentService, PaymentService}
import javax.inject.Inject
import kamon.Kamon

import scala.concurrent.ExecutionContext

/**
 * Each PeerActor is the connection between the server and the connected client.
 *
 * This acts as the layer to protect the websocket client to only receive what it requires,
 * as well as to validate the commands that the websocket client can actually execute based
 * on its known state.
 *
 * @param client the underlying websocket actor client
 * @param orderManager the actor managing the available orders
 */
class PeerActor(
    client: ActorRef,
    peerActorOps: PeerActorOps,
    orderManager: OrderManagerActor.Ref,
    messageFilter: PeerMessageFilterActor.Ref,
    connectionManager: ConnectionManagerActor.Ref,
    tradesRepository: TradesRepository.FutureImpl,
    peerUser: PeerUser,
    tradingPairsConfig: TradingPairsConfig
)(implicit
    ec: ExecutionContext
) extends Actor
    with ActorLogging {

  private implicit lazy val logger: LoggingAdapter = log

  private val activeClientsMetric = Kamon
    .gauge(
      name = "ws-active-clients",
      description = "The number of clients connected by the web socket"
    )
    .withTag("client-type", peerUser.getClass.getSimpleName)

  import PeerActor._

  private val subscriptionHandler: SubscriptionCommandHandler = new SubscriptionCommandHandler(
    orderManager = orderManager
  )

  private val orderHandler: OrderCommandHandler = new OrderCommandHandler(
    orderManager = orderManager,
    peerActorOps = peerActorOps
  )
  private val historicDataHandler: HistoricDataCommandHandler = new HistoricDataCommandHandler(tradesRepository)

  private val uncategorizedHandler: UncategorizedCommandHandler =
    new UncategorizedCommandHandler(orderManager, peerActorOps, tradingPairsConfig)
  private val orderFeeCommandHandler: OrderFeeCommandHandler = new OrderFeeCommandHandler(peerActorOps)
  private val channelCommandHandler: ChannelCommandHandler = new ChannelCommandHandler(peerActorOps)

  // TODO: remove this adding the TradeManagerActor
  var peerTrades: List[PeerTrade] = List.empty

  override def preStart(): Unit = {
    activeClientsMetric.increment()
    log.info(s"${peerUser.name}: Peer connected: $self")
    messageFilter.ref ! PeerMessageFilterActor.Command.PeerConnected(self)
    context become withState(PeerState.empty)
  }

  /**
   * The method is called when the websocket client is disconnected.
   */
  override def postStop(): Unit = {
    log.info(s"${peerUser.name}: Peer disconnected: $self")
    activeClientsMetric.decrement()
    messageFilter.ref ! PeerMessageFilterActor.Command.PeerDisconnected(self)
    connectionManager.ref ! ConnectionManagerActor.Command.Disconnect(peerUser)
    onClientDisconnected()
  }

  private def onClientDisconnected(): Unit = {
    orderManager.ref ! OrderManagerActor.Command.RemoveOrders(self)
    peerTrades.foreach { peerTrade =>
      peerActorOps.tryCancelingPayment(peerTrade.trade.executingOrder)(log)(peerUser)
      peerActorOps.tryCancelingPayment(peerTrade.trade.existingOrder)(log)(peerUser)
      peerTrade.secondOrder.peer ! Event.ServerEvent.MyMatchedOrderCanceled(peerTrade.trade)

    }
  }

  override def receive: Receive = { case x =>
    log.warning(s"${peerUser.name}: Unexpected message: $x")
  }

  private def processServerEvent(event: => Event.ServerEvent): Unit = {
    self ! event
  }

  /**
   * Handle commands from the web socket client.
   */
  private def handleCommand()(implicit ctx: CommandContext): CommandHandler.Result = ctx.cmd match {
    case cmd: UncategorizedCommand =>
      uncategorizedHandler.handle(cmd)

    case cmd: SubscriptionCommand =>
      subscriptionHandler.handle(cmd)

    case cmd: OrderCommand =>
      orderHandler.handle(cmd)

    case cmd: HistoricDataCommand =>
      historicDataHandler.handle(cmd)

    case cmd: OrderFeeCommand =>
      orderFeeCommandHandler.handle(cmd)

    case cmd: ChannelCommand =>
      channelCommandHandler.handle(cmd)
  }

  /**
   * This method handles the events from the order manager, it has these responsibilities:
   * - Updating the internal state on events related this client data.
   * - Forwarding the necessary events to the ws client.
   *
   * It is assumed that command responses are already handled by another method, hence, they can be safely ignored.
   */
  private def handleOrderManagerEvent(evt: OrderManagerActor.Event, state: PeerState): Unit = evt match {
    case OrderManagerActor.Event.MyOrderMatched(trade, orderMatchedWith) =>
      processServerEvent {
        Event.ServerEvent.MyOrderMatched(trade, orderMatchedWith.order)
      }

      val newState = state.`match`(trade, orderMatchedWith)
      context become withState(newState)

    case OrderManagerActor.Event.OrderPlaced(order) =>
      processServerEvent {
        Event.ServerEvent.OrderPlaced(order)
      }

    // command responses
    case _: OrderManagerActor.Event.OrdersRetrieved =>
    case _: OrderManagerActor.Event.Subscribed => ()
    case _: OrderManagerActor.Event.Unsubscribed => ()

    // useless event
    case _: OrderManagerActor.Event.OpenOrdersCleaned => ()
  }

  private def handleInternalMessage(state: PeerState, msg: InternalMessage): PeerState = msg match {
    case InternalMessage.OrderPlaced(order) =>
      state.add(order)

    case InternalMessage.OrderMatched(trade, otherPeerOrder) =>
      state.`match`(trade, otherPeerOrder)

    case InternalMessage.CleanOrders(tradingPair) =>
      state.removeOrders(tradingPair)

    case InternalMessage.SwapSuccess(tradeId) =>
      state.removeTrade(tradeId)

    case InternalMessage.SwapFailure(tradeId) =>
      state.removeTrade(tradeId)

    case InternalMessage.SwapTimeout(tradeId) =>
      // if the trade its still here that means we haven't received a swap success/failure so we cancel the trade,
      // otherwise we already received a swap success/failure and we don't need to do anything
      state.matched.find(_.trade.id == tradeId).foreach { peerTrade =>
        self ! Event.ServerEvent.MyMatchedOrderCanceled(peerTrade.trade)
      }

      state
  }

  private def withState(implicit state: PeerState): Receive = {
    def internalWithState: Receive = {
      case msg: WebSocketIncomingMessage =>
        implicit val ctx: CommandContext = CommandContext(msg.clientMessageId, msg.command, self, peerUser, state)

        handleCommand() match {
          case Result.Async => ()
          case Result.StateUpdated(newState) => context become withState(newState)
        }

      case msg @ TaggedCommandResponse(_, Event.CommandResponse.CancelOpenOrderResponse(Some(order))) =>
        /**
         * This is the same as the TaggedCommandResponse handler, but it cancels an open order once we get confirmation
         * from the order manager,
         *
         * TODO: Refactor the code to avoid this trick.
         */
        val message = WebSocketOutgoingMessage(state.messageCounter, Some(msg.requestId), msg.value)
        client ! message
        context become withState(state.consumeMessage.cancelOpenOrder(order.value.id))

      case msg: TaggedCommandResponse =>
        /**
         * Handle a tagged response, which generates a message id and forwards it to the actual web socket client
         *
         * The state is mutated to increase the message id, at this point, the state should have been updated by another message.
         */
        val message = WebSocketOutgoingMessage(state.messageCounter, Some(msg.requestId), msg.value)
        client ! message
        context become withState(state.consumeMessage)

      case msg: Event.ServerEvent.MyMatchedOrderCanceled =>
        val message = WebSocketOutgoingMessage(state.messageCounter, None, msg)
        client ! message
        val newState = state.consumeMessage.removeTrade(msg.trade.id)
        context become withState(newState)

      case msg: Event.ServerEvent =>
        val message = WebSocketOutgoingMessage(state.messageCounter, None, msg)
        client ! message
        context become withState(state.consumeMessage)

      case msg: OrderManagerActor.Event =>
        handleOrderManagerEvent(msg, state)

      case msg: InternalMessage =>
        val name = msg match {
          case _: InternalMessage.OrderPlaced => "InternalMessage.OrderPlaced"
          case _: InternalMessage.OrderMatched => "InternalMessage.OrderMatched"
          case _: InternalMessage.CleanOrders => "InternalMessage.CleanOrders"
          case _: InternalMessage.SwapSuccess => "InternalMessage.SwapSuccess"
          case _: InternalMessage.SwapFailure => "InternalMessage.SwapFailure"
          case _: InternalMessage.SwapTimeout => "InternalMessage.SwapTimeout"
        }

        val timer = Kamon.timer(name).withoutTags().start()

        val newState = handleInternalMessage(state, msg)
        context become withState(newState)

        timer.stop()

      /**
       * Debug unexpected messages.
       */
      case x => log.warning(s"${peerUser.name}: Unexpected message: $x")
    }
    peerTrades = state.matched
    internalWithState
  }
}

object PeerActor { self =>
  sealed abstract class Ref(val actor: ActorRef)

  class Factory @Inject() (
      orderManager: OrderManagerActor.Ref,
      messageFilter: PeerMessageFilterActor.Ref,
      connectionManager: connection.ConnectionManagerActor.Ref,
      orderValidator: OrderValidator,
      tradesRepository: TradesRepository.FutureImpl,
      feeService: FeeService,
      paymentService: PaymentService,
      featureFlags: FeatureFlags,
      channelService: ChannelService,
      tradingPairsConfig: TradingPairsConfig,
      discordHelper: DiscordHelper,
      clientService: ClientService,
      makerPaymentService: MakerPaymentService,
      connextHelper: ConnextHelper,
      channelRentalConfig: ChannelRentalConfig
  )(implicit
      system: ActorSystem,
      ec: ExecutionContext
  ) {

    def build(
        client: ActorRef,
        peerUser: PeerUser,
        overridePaysFees: Option[Boolean] = None,
        overrideName: Option[String] = None
    ): Ref = {
      val newFeatureFlags = overridePaysFees
        .map { paysFees =>
          featureFlags.copy(feesEnabled = paysFees)
        }
        .getOrElse(featureFlags)

      val ops = new PeerActorOps(
        orderManager,
        orderValidator,
        feeService,
        paymentService,
        newFeatureFlags,
        channelService,
        discordHelper,
        clientService,
        makerPaymentService,
        connextHelper,
        channelRentalConfig
      )
      val props = Props(
        new PeerActor(
          client,
          ops,
          orderManager,
          messageFilter,
          connectionManager,
          tradesRepository,
          peerUser,
          tradingPairsConfig
        )
      )

      val actor = overrideName
        .map(name => system.actorOf(props, name))
        .getOrElse(system.actorOf(props))

      new Ref(actor) {}
    }
  }

  sealed trait InternalMessage

  object InternalMessage {
    final case class OrderMatched(trade: Trade, otherPeerOrder: PeerOrder) extends InternalMessage
    final case class OrderPlaced(order: TradingOrder) extends InternalMessage
    final case class CleanOrders(tradingPair: TradingPair) extends InternalMessage
    final case class SwapSuccess(tradeID: Trade.Id) extends InternalMessage
    final case class SwapFailure(tradeID: Trade.Id) extends InternalMessage
    final case class SwapTimeout(tradeID: Trade.Id) extends InternalMessage
  }
}
