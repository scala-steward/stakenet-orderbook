package io.stakenet.orderbook.actors.orders

import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.trading.{OrderSide, TradingPair}

import scala.collection.immutable.{TreeMap, TreeSet}

case class GroupedOrders(orders: Map[TradingPair, SortedOrders]) {

  def add(pair: TradingPair)(order: pair.LimitOrder): GroupedOrders = {
    val pairOrders = orders.getOrElse(pair, SortedOrders.empty(pair))
    val pairOrder = pairOrders.pair
      .useLimitOrder(order)
      .getOrElse(throw new RuntimeException("Impossible"))

    copy(orders.updated(pair, pairOrders.addOrder(pairOrder)))
  }

  def remove(pair: TradingPair)(order: pair.LimitOrder): GroupedOrders = {
    val pairOrders = orders.getOrElse(pair, SortedOrders.empty(pair))
    val pairOrder = pairOrders.pair
      .useLimitOrder(order)
      .getOrElse(throw new RuntimeException("Impossible"))

    copy(orders.updated(pair, pairOrders.removeOrder(pairOrder)))
  }

  def availableFor(pair: TradingPair, side: OrderSide): TreeMap[Satoshis, TreeSet[pair.LimitOrder]] = {
    val pairOrders = orders.getOrElse(pair, SortedOrders.empty(pair))

    side match {
      case OrderSide.Sell => pairOrders.buyOrders.asInstanceOf[TreeMap[Satoshis, TreeSet[pair.LimitOrder]]]
      case OrderSide.Buy => pairOrders.sellOrders.asInstanceOf[TreeMap[Satoshis, TreeSet[pair.LimitOrder]]]
    }
  }

  def summaryView(pair: TradingPair): (OrderSummaryView.BuySide, OrderSummaryView.SellSide) = {
    val pairOrders = orders.getOrElse(pair, SortedOrders.empty(pair))
    (pairOrders.buyView, pairOrders.sellView)
  }
}

object GroupedOrders {
  val empty = new GroupedOrders(Map.empty)

  def apply(orders: List[TradingPair#LimitOrder]): GroupedOrders = {
    orders.foldLeft(empty) { (groupedOrders, order) =>
      val pair = order.tradingPair
      val typedOrder = pair
        .useLimitOrder(order)
        .getOrElse(throw new RuntimeException("Impossible"))

      groupedOrders.add(pair)(typedOrder)
    }
  }
}
