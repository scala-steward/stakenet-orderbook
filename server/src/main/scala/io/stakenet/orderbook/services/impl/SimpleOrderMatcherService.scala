package io.stakenet.orderbook.services

package impl

import io.stakenet.orderbook.models.trading._

class SimpleOrderMatcherService extends OrderMatcherService {

  override def matchOrder(orderMatching: TradingOrderMatching): Option[Trade] = {
    orderMatching.candidate match {
      case x: orderMatching.pair.MarketOrder => matchMarketOrder(orderMatching.pair)(x, orderMatching.availableAsList)
      case x: orderMatching.pair.LimitOrder => matchLimitOrder(orderMatching.pair)(x, orderMatching.availableAsList)
    }
  }

  /**
   * For matching a market order:
   * - Only limit orders are considered.
   * - For buy order, the sell order with the lowest price is selected.
   * - For sell order, the buy order with the highest price is selected
   * TODO: It might be worth to ensure the order can be fulfilled instead
   */
  private def matchMarketOrder(pair: TradingPair)(
      source: pair.MarketOrder,
      available: List[pair.LimitOrder]
  ): Option[Trade] = {
    val targetOrders = available.collect {
      case candidate if source.matches(candidate) => candidate
    }

    val orderMaybe = source.side match {
      case OrderSide.Buy => findMinPrice(pair)(targetOrders)
      case OrderSide.Sell => findMaxPrice(pair)(targetOrders)
    }

    orderMaybe.map { order =>
      Trade.from(pair)(executingOrder = source, existingOrder = order)
    }
  }

  /**
   * For matching a limit order:
   * - Only limit orders are considered.
   * - For buy order, the sell order with the lowest price is selected.
   * - For sell order, the buy order with the highest price is selected
   *  Note: Market orders aren't supposed to be available because this can lead to a peer
   *       selling an overpriced order when there are only market orders to match.
   *
   * TODO: It might be worth to ensure the order can be fulfilled instead
   */
  private def matchLimitOrder(pair: TradingPair)(
      source: pair.LimitOrder,
      available: List[pair.LimitOrder]
  ): Option[Trade] = {
    val targetOrders = available.collect {
      case candidate if source.matches(candidate) => candidate
    }

    val orderMaybe = source.side match {
      case OrderSide.Buy => findMinPrice(pair)(targetOrders)
      case OrderSide.Sell => findMaxPrice(pair)(targetOrders)
    }

    orderMaybe.map { order =>
      Trade.from(pair)(executingOrder = source, existingOrder = order)
    }
  }

  /**
   * Find the highest price in the candidate orders
   * in case of two orders with the same price, it will take the one with the higher funds
   */
  private def findMaxPrice(pair: TradingPair)(targetOrders: List[pair.LimitOrder]): Option[pair.LimitOrder] = {
    if (targetOrders.isEmpty) {
      None
    } else {
      val maxPrice = targetOrders.maxBy(_.details.price).details.price
      val bestOrder =
        targetOrders.filter(_.details.price == maxPrice).min // min gets the one with higher funds on price ties
      Some(bestOrder)
    }
  }

  /**
   * find the lowest price in the candidate orders
   * in case of two orders with the same price, it will take the one with the higher funds
   */
  private def findMinPrice(pair: TradingPair)(targetOrders: List[pair.LimitOrder]): Option[pair.LimitOrder] = {
    if (targetOrders.isEmpty) {
      None
    } else {
      val minPrice = targetOrders.minBy(_.details.price).details.price
      val bestOrder =
        targetOrders.filter(_.details.price == minPrice).min // min gets the one with higher funds on price ties
      Some(bestOrder)
    }
  }
}
