package io.stakenet.orderbook.actors.orders

import akka.actor._
import io.stakenet.orderbook.actors.{ActorMessage, InstantMessage, ScheduledMessage}
import io.stakenet.orderbook.config.TradesConfig
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.{OrderSummary, Trade, TradingOrder, TradingPair}
import io.stakenet.orderbook.services.OrderMatcherService
import kamon.Kamon

/**
 * This actor acts like the actual orderbook, it has the active orders and supports commands for
 * updating the book.
 *
 * @param orderMatcher the service that knows what orders should be paired/matched.
 */
class OrderManagerActor(orderMatcher: OrderMatcherService, tradesConfig: TradesConfig) extends Actor with ActorLogging {

  import OrderManagerActor._

  override def preStart(): Unit = {
    log.info("OrderManager starting")
    context become withState(OrderManagerState.empty)
  }

  override def receive: Receive = { case x =>
    log.info(s"Unexpected message: $x")
  }

  private def handle(cmd: Command)(implicit state: OrderManagerState) = cmd match {
    case Command.GetTradingOrders(pair) =>
      val messages = OrderManagerOps.sendOrders(pair, sender())
      sendMessages(messages)

    case Command.CancelOrder(id, owner) =>
      val result = OrderManagerOps.cancelOrder(id, owner = owner, sender = sender())
      sendMessages(result.messages)
      context become withState(result.newState)

    case Command.PlaceOrder(order, clientId, owner) =>
      val peerOrder = PeerOrder(clientId, owner, order)

      val result = OrderManagerOps.placeOrder(
        log,
        orderMatcher,
        peerOrder,
        sender = sender(),
        tradesConfig
      )
      sendMessages(result.messages)
      context become withState(result.newState)

    case Command.RemoveOrders(owner) =>
      val result = OrderManagerOps.removeOrders(owner)
      log.info(s"RemoveOrders from peer $owner")
      sendMessages(result.messages)
      context become withState(result.newState)

    case Command.GetAllOrders =>
      val messages = OrderManagerOps.sendAllOrders(log, sender())
      sendMessages(messages)

    case Command.Subscribe(pair, subscriber, includeOrderSummary) =>
      val result = OrderManagerOps.subscribe(pair, includeOrderSummary, subscriber, sender())
      sendMessages(result.messages)
      context become withState(result.newState)

    case Command.Unsubscribe(pair, subscriber) =>
      val newState = state.unsubscribe(pair, subscriber)
      sender() ! Event.Unsubscribed(pair)
      context become withState(newState)

    case Command.GetTradingOrderById(orderId) =>
      val messages = OrderManagerOps.sendOrder(orderId, sender())
      sendMessages(messages)

    case Command.CleanOpenOrders(tradingPair, owner) =>
      val result = OrderManagerOps.cleanOpenOrders(tradingPair, owner, sender())
      sendMessages(result.messages)
      context become withState(result.newState)

    case Command.UpdateSwapSuccess(trade) =>
      val result = OrderManagerOps.swapSuccess(trade)
      sendMessages(result.messages)

    case Command.UpdateSwapFailure(trade) =>
      val result = OrderManagerOps.swapFailure(trade)
      sendMessages(result.messages)
  }

  private def sendMessages(messages: List[ActorMessage]) = messages.foreach {
    case InstantMessage(destination, message) =>
      destination ! message
    case ScheduledMessage(destination, message, delay) =>
      context.system.scheduler.scheduleOnce(delay, destination, message)(context.dispatcher)
  }

  private def withState(implicit state: OrderManagerState): Receive = {

    /**
     * Handle commands.
     */
    case cmd: Command =>
      val name = cmd match {
        case _: Command.PlaceOrder => "PlaceOrder"
        case _: Command.CancelOrder => "CancelOrder"
        case _: Command.GetTradingOrders => "GetTradingOrders"
        case _: Command.RemoveOrders => "RemoveOrders"
        case Command.GetAllOrders => "GetAllOrders"
        case _: Command.Subscribe => "Subscribe"
        case _: Command.Unsubscribe => "Unsubscribe"
        case _: Command.GetTradingOrderById => "GetTradingOrderById"
        case _: Command.CleanOpenOrders => "CleanOpenOrders"
        case _: Command.UpdateSwapSuccess => "UpdateSwapSuccess"
        case _: Command.UpdateSwapFailure => "UpdateSwapFailure"
        case _ => "Unknown"
      }
      // TODO: cmd.getClass.getSimpleName fails
      val timer = Kamon.timer(name).withoutTags().start()

      handle(cmd)

      timer.stop()

    /**
     * Debug logs.
     */
    case x => log.info(s"withState - Unexpected message: $x")
  }
}

object OrderManagerActor {

  def props(orderMatcher: OrderMatcherService, tradesConfig: TradesConfig): Props = {
    Props(new OrderManagerActor(orderMatcher, tradesConfig))
  }

  final class Ref private (val ref: ActorRef)

  object Ref {

    def apply(orderMatcher: OrderMatcherService, tradesConfig: TradesConfig, name: String = "order-manager")(implicit
        system: ActorSystem
    ): Ref = {
      val actor = system.actorOf(props(orderMatcher, tradesConfig), name)
      new Ref(actor)
    }
  }

  sealed trait Command extends Product with Serializable

  object Command {
    // includes the actor that owns the placed order, this is required to avoid issues while using the ask pattern
    // that pattern creates a temporal actor that's killed after the first response
    final case class PlaceOrder(order: TradingOrder, clientId: ClientId, owner: ActorRef) extends Command
    final case class CancelOrder(id: OrderId, owner: ActorRef) extends Command
    final case class GetTradingOrders(pair: TradingPair) extends Command
    final case class RemoveOrders(owner: ActorRef) extends Command
    final case object GetAllOrders extends Command
    final case class Subscribe(pair: TradingPair, subscriber: ActorRef, retrieveOrdersSummary: Boolean) extends Command
    final case class Unsubscribe(pair: TradingPair, subscriber: ActorRef) extends Command
    final case class GetTradingOrderById(orderId: OrderId) extends Command
    final case class CleanOpenOrders(pair: TradingPair, subscriber: ActorRef) extends Command
    final case class UpdateSwapSuccess(trade: Trade) extends Command
    final case class UpdateSwapFailure(trade: Trade) extends Command
  }

  sealed trait Event extends Product with Serializable

  object Event {
    final case class OrderPlaced(order: TradingOrder) extends Event

    final case class Subscribed(pair: TradingPair, bidsSummary: List[OrderSummary], asksSummary: List[OrderSummary])
        extends Event
    final case class Unsubscribed(pair: TradingPair) extends Event
    final case class MyOrderMatched(trade: Trade, orderMatchedWith: PeerOrder) extends Event
    final case class OrdersRetrieved(orders: List[TradingOrder]) extends Event
    final case class OpenOrdersCleaned(tradingPair: TradingPair, ordersRemoved: List[OrderId]) extends Event
  }
}
