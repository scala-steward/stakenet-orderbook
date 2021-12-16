package io.stakenet.orderbook.actors.orders

import akka.actor.ActorRef
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.trading.TradingPair

/**
 * The order book state.
 *
 * @param orderPeerMap the way to get the peer that placed an order
 */
private[orders] case class OrderManagerState(
    private val orderPeerMap: Map[OrderId, PeerOrder],
    private val peerOrdersMap: Map[ActorRef, Set[OrderId]],
    private val activeSubscriptions: Map[TradingPair, Set[ActorRef]],
    groupedOrders: GroupedOrders
) {

  /**
   * This is a very slow operation, use it with care.
   */
  def getOrders: List[PeerOrder] = {
    orderPeerMap.values.toList
  }

  /**
   * Add an order.
   *
   * This takes O(log N).
   */
  def add(order: PeerOrder): OrderManagerState = {
    val pair = order.order.pair
    val limitOrder = pair.useLimitOrder(order.order.value).getOrElse(throw new RuntimeException("Impossible"))

    val newOrderPeerMap = orderPeerMap + (order.order.value.id -> order)
    val newPeerOrders = peerOrdersMap.getOrElse(order.peer, Set.empty) + order.order.value.id
    val newPeerOrdersMap = peerOrdersMap + (order.peer -> newPeerOrders)
    val newGroupedOrders = groupedOrders.add(pair)(limitOrder)
    copy(
      orderPeerMap = newOrderPeerMap,
      peerOrdersMap = newPeerOrdersMap,
      groupedOrders = newGroupedOrders
    )
  }

  /**
   * Find an order.
   *
   * This takes O(1).
   */
  def find(owner: ActorRef): Set[PeerOrder] = {
    peerOrdersMap.getOrElse(owner, Set.empty).map(orderPeerMap.apply)
  }

  /**
   * Find an order.
   *
   * This takes O(1).
   */
  def find(orderId: OrderId): Option[PeerOrder] = {
    orderPeerMap.get(orderId)
  }

  /**
   * Find an order.
   *
   * This takes O(1).
   */
  def find(orderId: OrderId, owner: ActorRef): Option[PeerOrder] = {
    find(orderId)
      .filter(_.peer == owner)
  }

  /**
   * Find an order.
   *
   * This takes O(M), M being the number of orders on the owner.
   */
  def find(owner: ActorRef, pair: TradingPair): Set[PeerOrder] = {
    find(owner).filter(_.order.pair == pair)
  }

  def remove(owner: ActorRef): OrderManagerState = {
    TradingPair.values.foldLeft(this) { case (acc, pair) =>
      acc.remove(pair, owner)
    }
  }

  def remove(pair: TradingPair, owner: ActorRef): OrderManagerState = {
    val removedOrders = peerOrdersMap
      .getOrElse(owner, Set.empty)
      .map(orderPeerMap.apply)
      .filter(_.order.pair == pair)

    removedOrders
      .foldLeft(this) { case (acc, order) =>
        acc.remove(order)
      }
      .unsubscribe(pair, owner)
  }

  def remove(order: PeerOrder): OrderManagerState = {
    // update newOrderPeerMap, newPeerOrdersMap
    val newPeerOrders = peerOrdersMap.getOrElse(order.peer, Set.empty) - order.order.value.id
    val newPeerOrdersMap = if (newPeerOrders.isEmpty) {
      peerOrdersMap - order.peer
    } else {
      peerOrdersMap + (order.peer -> newPeerOrders)
    }
    val newOrderPeerMap = orderPeerMap - order.order.value.id

    // update grouped orders
    val pair = order.order.pair
    val limitOrder = pair.useLimitOrder(order.order.value).getOrElse(throw new RuntimeException("Impossible"))
    val newGroupedOrders = groupedOrders.remove(pair)(limitOrder)

    // no need to update subscriptions
    copy(
      orderPeerMap = newOrderPeerMap,
      peerOrdersMap = newPeerOrdersMap,
      groupedOrders = newGroupedOrders
    )
  }

  def subscribe(pair: TradingPair, peer: ActorRef): OrderManagerState = {
    val newCurrencySubscriptor = (pair, subscriptors(pair) + peer)
    val newSubscriptions = activeSubscriptions + newCurrencySubscriptor
    copy(activeSubscriptions = newSubscriptions)
  }

  def unsubscribe(pair: TradingPair, peer: ActorRef): OrderManagerState = {
    val newCurrencySubscriptor = (pair, subscriptors(pair) - peer)
    val newSubscriptions = activeSubscriptions + newCurrencySubscriptor
    copy(activeSubscriptions = newSubscriptions)
  }

  def subscriptors(pair: TradingPair): Set[ActorRef] = {
    activeSubscriptions.getOrElse(pair, Set.empty)
  }
}

object OrderManagerState {
  val empty: OrderManagerState = OrderManagerState(Map.empty, Map.empty, Map.empty, GroupedOrders.empty)
}
