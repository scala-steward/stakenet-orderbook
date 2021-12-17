package io.stakenet.orderbook.actors.orders

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import io.stakenet.orderbook.actors.peers.PeerActor.InternalMessage
import io.stakenet.orderbook.actors.{ActorMessage, InstantMessage, ScheduledMessage, UpdateStateResult, peers}
import io.stakenet.orderbook.config.TradesConfig
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.services.OrderMatcherService

// TODO: should i exclude myself from notifications ?
private[orders] object OrderManagerOps {

  import OrderManagerActor._

  /** Find the orders involving the given trading pair, and send them to the given actor.
    */
  def sendOrders(pair: TradingPair, replyTo: ActorRef)(implicit
      state: OrderManagerState
  ): List[ActorMessage] = {
    val (summaryBuy, summarySell) = state.groupedOrders.summaryView(pair)

    val groupedBids = summaryBuy.values.map(x => OrderSummary(x._1, x._2)).toList.reverse
    val groupedAsks = summarySell.values.map(x => OrderSummary(x._1, x._2)).toList
    // TODO: Decouple from peer actor
    List(
      InstantMessage(
        replyTo,
        peers.protocol.Event.CommandResponse
          .GetOpenOrdersResponse(pair, bidsSummary = groupedBids, asksSummary = groupedAsks)
      )
    )
  }

  /** A peer is cancelling the given order, sends a receipt on completion.
    *
    * @param id
    *   the id of the order to cancel
    * @param owner
    *   the peer to notify on successful cancellation
    * @param state
    *   the current state
    * @return
    *   the new state after cancelling the given order
    */
  def cancelOrder(id: OrderId, owner: ActorRef, sender: ActorRef)(implicit
      state: OrderManagerState
  ): UpdateStateResult[OrderManagerState] = {
    val cancelledOrder = state.find(id, owner)

    cancelledOrder match {
      case Some(peerOrder) =>
        // TODO; Decouple from PeerActor
        val ownerEvents = List(
          InstantMessage(sender, peers.protocol.Event.CommandResponse.CancelOpenOrderResponse(Some(peerOrder.order)))
        )

        // TODO; Decouple from PeerActor
        val notificationEvents = for {
          subscriber <- state.subscriptors(peerOrder.order.pair)
        } yield InstantMessage(
          subscriber,
          peers.protocol.Event.ServerEvent.OrderCanceled(peerOrder.order)
        )

        UpdateStateResult(state.remove(peerOrder), ownerEvents ++ notificationEvents)
      case None =>
        // TODO; Decouple from PeerActor
        val ownerEvents = List(
          InstantMessage(sender, peers.protocol.Event.CommandResponse.CancelOpenOrderResponse(None))
        )

        UpdateStateResult(state, ownerEvents)
    }
  }

  /** Place an order, if there is a candidate to match the new order, match them, otherwise, add the order to the
    * existing list.
    *
    *   - The peer gets notified if the order is placed or rejected.
    *   - If the order is matched, both peers are notified.
    *
    * @param log
    *   the logger
    * @param orderMatcher
    *   the service that knows how to match orders
    * @param peerOrder
    *   the order to place with its owner information
    * @param state
    *   the curent state
    * @return
    *   the new state after placing or matching the order
    */
  def placeOrder(
      log: LoggingAdapter,
      orderMatcher: OrderMatcherService,
      peerOrder: PeerOrder,
      sender: ActorRef,
      tradesConfig: TradesConfig
  )(implicit
      state: OrderManagerState
  ): UpdateStateResult[OrderManagerState] = {
    val order = peerOrder.order
    val orderOwner = peerOrder.peer

    def handleOrderNotMatched: UpdateStateResult[OrderManagerState] = {
      order.fold(
        onLimitOrder = _ => {
          log.info(s"The order $order from $orderOwner wasn't matched, added to the queue")

          val actorMessages = List(InstantMessage(sender, Event.OrderPlaced(order)))

          val notificationEvents = for {
            subscriber <- state.subscriptors(order.pair)
          } yield InstantMessage(subscriber, Event.OrderPlaced(order))

          UpdateStateResult(state.add(peerOrder), actorMessages ++ notificationEvents.toList)
        },
        onMarketOrder = _ => {
          log.info(s"The order $order from $orderOwner wasn't matched, discarding")
          val actorMessages = List(
            InstantMessage(
              sender,
              // TODO: Decouple from peer actor
              peers.protocol.Event.CommandResponse
                .CommandFailed("There aren't orders to fulfill your market order, try later")
            )
          )
          UpdateStateResult(state, actorMessages)
        }
      )
    }

    def handleOrderMatched(trade: Trade): UpdateStateResult[OrderManagerState] = {
      log.info(s"Order $order from peer = $orderOwner was matched")
      val pairedOrder = trade.existingOrder
      val matchedPeerOrder = state
        .find(pairedOrder)
        .getOrElse(
          // Impossible state
          throw new RuntimeException(s"The order $order from $orderOwner was matched to an unknown one: $pairedOrder")
        )

      if (matchedPeerOrder.peer == orderOwner) {
        log.info(s"The order $order was matched with an order from the same owner, discarding")
        val actorMessages = List(
          InstantMessage(
            sender,
            peers.protocol.Event.CommandResponse.CommandFailed("Your order was matched with one of your own orders")
          )
        )

        UpdateStateResult(state, actorMessages)
      } else {
        val actorMessages = List(
          InstantMessage(sender, Event.MyOrderMatched(trade, matchedPeerOrder)),
          InstantMessage(matchedPeerOrder.peer, Event.MyOrderMatched(trade, peerOrder)),
          ScheduledMessage(orderOwner, InternalMessage.SwapTimeout(trade.id), tradesConfig.swapTimeout),
          ScheduledMessage(matchedPeerOrder.peer, InternalMessage.SwapTimeout(trade.id), tradesConfig.swapTimeout)
        )

        val notificationEvents = for {
          subscriber <- state.subscriptors(order.pair)
        } yield InstantMessage(
          subscriber,
          peers.protocol.Event.ServerEvent.OrdersMatched(trade)
        ) // TODO: Decouple from peer actor

        UpdateStateResult(state.remove(matchedPeerOrder), actorMessages ++ notificationEvents.toList)
      }
    }

    log.info(s"Order received: $order from $orderOwner")

    val available = state.groupedOrders.availableFor(order.pair, order.value.side)
    val orderMatching = TradingOrderMatching(order.pair)(order.value, available)
    orderMatcher.matchOrder(orderMatching).fold(handleOrderNotMatched)(handleOrderMatched)
  }

  /** Remove all the orders submitted by the given actor.
    *
    * @param owner
    *   the actor
    * @param state
    *   the current state
    * @return
    *   the new state after removing the orders
    */
  def removeOrders(owner: ActorRef)(implicit state: OrderManagerState): UpdateStateResult[OrderManagerState] = {
    // TODO: Decouple from PeerActor
    val notificationEvents = for {
      cancelledOrder <- state.find(owner)
      subscriber <- state.subscriptors(cancelledOrder.order.pair)
    } yield InstantMessage(
      subscriber,
      peers.protocol.Event.ServerEvent.OrderCanceled(cancelledOrder.order)
    )

    UpdateStateResult(state.remove(owner), notificationEvents.toList)
  }

  /** Find and send all orders to the given actor
    *
    * @param log
    *   the logger
    * @param replyTo
    *   the actor interested in getting all the orders
    * @param state
    *   the current state
    */
  def sendAllOrders(log: LoggingAdapter, replyTo: ActorRef)(implicit state: OrderManagerState): List[ActorMessage] = {
    val orders = state.getOrders.map(_.order)
    log.info(s"GetAllOrders -> ${orders.size}")
    List(InstantMessage(replyTo, Event.OrdersRetrieved(orders)))
  }

  def sendOrder(orderId: OrderId, replyTo: ActorRef)(implicit
      state: OrderManagerState
  ): List[ActorMessage] = {

    val order = state.find(orderId).map(_.order)
    // TODO: Decouple from PeerActor
    val result = peers.protocol.Event.CommandResponse.GetOpenOrderByIdResponse(order)

    List(InstantMessage(replyTo, result))
  }

  /** Remove all orders from one trading pair submitted by the given actor.
    *
    * @param owner
    *   the actor
    * @param tradingPair
    *   the trading pair to be removed
    * @param state
    *   the current state
    * @return
    *   the new state after removing the open orders
    */
  def cleanOpenOrders(tradingPair: TradingPair, owner: ActorRef, replyTo: ActorRef)(implicit
      state: OrderManagerState
  ): UpdateStateResult[OrderManagerState] = {
    val removedOrders = state.find(owner, tradingPair)

    // TODO: Decouple from PeerActor
    val notificationEvents = for {
      cancelledOrder <- removedOrders
      subscriber <- state.subscriptors(cancelledOrder.order.pair)
    } yield InstantMessage(
      subscriber,
      peers.protocol.Event.ServerEvent
        .OrderCanceled(cancelledOrder.order)
    )

    val ownerEvents = List(
      InstantMessage(replyTo, Event.OpenOrdersCleaned(tradingPair, removedOrders.map(_.order.value.id).toList))
    )
    UpdateStateResult(state.remove(tradingPair, owner), notificationEvents.toList ++ ownerEvents)
  }

  def swapSuccess(trade: Trade)(implicit
      state: OrderManagerState
  ): UpdateStateResult[OrderManagerState] = {
    val notificationEvents = for {
      subscriber <- state.subscriptors(trade.pair)
    } yield InstantMessage(
      subscriber,
      peers.protocol.Event.ServerEvent.SwapSuccess(trade)
    )

    UpdateStateResult(state, notificationEvents.toList)
  }

  def swapFailure(trade: Trade)(implicit
      state: OrderManagerState
  ): UpdateStateResult[OrderManagerState] = {
    val notificationEvents = for {
      subscriber <- state.subscriptors(trade.pair)
    } yield InstantMessage(
      subscriber,
      peers.protocol.Event.ServerEvent.SwapFailure(trade)
    )

    UpdateStateResult(state, notificationEvents.toList)
  }

  def subscribe(pair: TradingPair, includeOrderSummary: Boolean, subscriber: ActorRef, replyTo: ActorRef)(implicit
      state: OrderManagerState
  ): UpdateStateResult[OrderManagerState] = {
    val newState = state.subscribe(pair, subscriber)

    val (bidsSummary, asksSummary) = if (includeOrderSummary) {
      val (summaryBuy, summarySell) = state.groupedOrders.summaryView(pair)
      (
        summaryBuy.values.map(x => OrderSummary(x._1, x._2)).toList.reverse,
        summarySell.values.map(x => OrderSummary(x._1, x._2)).toList
      )
    } else {
      (List.empty, List.empty)
    }

    val events = List(
      InstantMessage(
        replyTo,
        Event.Subscribed(pair, bidsSummary = bidsSummary, asksSummary = asksSummary)
      )
    )
    UpdateStateResult(newState, events)
  }
}
