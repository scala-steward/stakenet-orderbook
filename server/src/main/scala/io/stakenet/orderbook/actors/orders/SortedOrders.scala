package io.stakenet.orderbook.actors.orders

import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.trading.{OrderSide, TradingPair}

import scala.collection.immutable.{TreeMap, TreeSet}

trait SortedOrders {
  val pair: TradingPair

  val sellOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]
  val sellView: OrderSummaryView.SellSide // asks

  val buyOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]
  val buyView: OrderSummaryView.BuySide // bids

  def copy(
      newSellOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]] = sellOrders,
      newSellView: OrderSummaryView.SellSide = sellView,
      newBuyOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]] = buyOrders,
      newBuyView: OrderSummaryView.BuySide = buyView
  ): SortedOrders = {
    SortedOrders(pair)(
      _sellOrders = newSellOrders,
      _sellView = newSellView,
      _buyOrders = newBuyOrders,
      _buyView = newBuyView
    )
  }

  def addOrder(order: pair.LimitOrder): SortedOrders = {
    order.side match {
      case OrderSide.Sell =>
        val priceOrders = sellOrders.getOrElse(order.details.price, TreeSet.empty[pair.LimitOrder])
        val newSellOrders = sellOrders.updated(order.details.price, priceOrders + order)

        val newSellView = sellView.add(price = order.details.price, amount = order.details.funds)
        copy(newSellOrders = newSellOrders, newSellView = newSellView)

      case OrderSide.Buy =>
        val priceOrders = buyOrders.getOrElse(order.details.price, TreeSet.empty[pair.LimitOrder])
        val newBuyOrders = buyOrders.updated(order.details.price, priceOrders + order)

        val newBuyView = buyView.add(price = order.details.price, amount = order.details.funds)
        copy(newBuyOrders = newBuyOrders, newBuyView = newBuyView)
    }
  }

  def removeOrder(order: pair.LimitOrder): SortedOrders = {
    order.side match {
      case OrderSide.Sell =>
        val priceOrders = sellOrders.getOrElse(order.details.price, TreeSet.empty[pair.LimitOrder])
        val newPriceOrders = priceOrders - order
        val newSellOrders = if (newPriceOrders.isEmpty) {
          sellOrders - order.details.price
        } else {
          sellOrders.updated(order.details.price, newPriceOrders)
        }

        val newSellView = sellView.remove(price = order.details.price, amount = order.details.funds)
        copy(newSellOrders = newSellOrders, newSellView = newSellView)

      case OrderSide.Buy =>
        val priceOrders = buyOrders.getOrElse(order.details.price, TreeSet.empty[pair.LimitOrder])
        val newPriceOrders = priceOrders - order
        val newBuyOrders = if (newPriceOrders.isEmpty) {
          buyOrders - order.details.price
        } else {
          buyOrders.updated(order.details.price, newPriceOrders)
        }

        val newBuyView = buyView.remove(price = order.details.price, amount = order.details.funds)
        copy(newBuyOrders = newBuyOrders, newBuyView = newBuyView)
    }
  }
}

object SortedOrders {

  def apply(
      _pair: TradingPair
  )(
      _sellOrders: TreeMap[Satoshis, TreeSet[_pair.LimitOrder]],
      _sellView: OrderSummaryView.SellSide,
      _buyOrders: TreeMap[Satoshis, TreeSet[_pair.LimitOrder]],
      _buyView: OrderSummaryView.BuySide
  ): SortedOrders = {
    new SortedOrders {
      override val pair: _pair.type = _pair
      override val sellOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]] = _sellOrders
      override val sellView: OrderSummaryView.SellSide = _sellView
      override val buyOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]] = _buyOrders
      override val buyView: OrderSummaryView.BuySide = _buyView
    }
  }

  def empty(pair: TradingPair): SortedOrders = {
    apply(pair)(TreeMap.empty, OrderSummaryView.SellSide.empty, TreeMap.empty, OrderSummaryView.BuySide.empty)
  }
}
