package io.stakenet.orderbook.models.trading

import io.stakenet.orderbook.actors.orders.GroupedOrders
import io.stakenet.orderbook.models.Satoshis

import scala.collection.immutable.{TreeMap, TreeSet}

trait TradingOrderMatching {
  val pair: TradingPair
  val candidate: pair.Order
  val available: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]
  lazy val availableAsList: List[pair.LimitOrder] = available.values.flatMap(_.toList).toList
}

object TradingOrderMatching {

  def apply(
      _pair: TradingPair
  )(
      _candidate: _pair.Order,
      _availableAsMap: TreeMap[Satoshis, TreeSet[_pair.LimitOrder]]
  ): TradingOrderMatching = {
    new TradingOrderMatching {
      override val pair: _pair.type = _pair
      override val candidate: pair.Order = _candidate
      override val available: TreeMap[Satoshis, TreeSet[pair.LimitOrder]] = _availableAsMap
    }
  }

  def fromList(
      _pair: TradingPair
  )(
      _candidate: _pair.Order,
      _available: List[_pair.LimitOrder]
  ): TradingOrderMatching = {
    val groupedOrders = GroupedOrders(_available)
    val initial = groupedOrders.availableFor(_pair, OrderSide.Buy)
    val result =
      groupedOrders.availableFor(_pair, OrderSide.Sell).values.flatMap(_.toList).foldLeft(initial) { case (acc, cur) =>
        val tree = acc.getOrElse(cur.details.price, TreeSet.empty[_pair.LimitOrder]) + cur
        acc + (cur.details.price -> tree)
      }
    apply(_pair)(_candidate, result)
  }

  def fromMap(
      _pair: TradingPair
  )(
      _candidate: _pair.Order,
      _availableAsMap: TreeMap[Satoshis, TreeSet[_pair.LimitOrder]]
  ): TradingOrderMatching = {
    apply(_pair)(_candidate, _availableAsMap)
  }
}
