package io.stakenet.orderbook.services.impl

import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.services.OrderMatcherService

import scala.collection.immutable.{TreeMap, TreeSet}

class TreeBasedOrderMatcherService extends OrderMatcherService {

  override def matchOrder(orderMatching: TradingOrderMatching): Option[Trade] = {
    orderMatching.candidate match {
      case x: orderMatching.pair.MarketOrder => matchMarketOrder(orderMatching.pair)(x, orderMatching.available)
      case x: orderMatching.pair.LimitOrder => matchLimitOrder(orderMatching.pair)(x, orderMatching.available)
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
      available: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]
  ): Option[Trade] = {
    val orderMaybe = source.side match {
      case OrderSide.Buy => findMinPrice(pair)(available)
      case OrderSide.Sell => findMaxPrice(pair)(available)
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
      available: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]
  ): Option[Trade] = {
    val orderMaybe = source.side match {
      case OrderSide.Buy => findMinPrice(pair)(available).filter(_.details.price <= source.details.price)
      case OrderSide.Sell => findMaxPrice(pair)(available).filter(_.details.price >= source.details.price)
    }

    orderMaybe.map { order =>
      Trade.from(pair)(executingOrder = source, existingOrder = order)
    }
  }

  /**
   * Find the highest price in the candidate orders
   * in case of two orders with the same price, it will take the one with the higher funds
   */
  private def findMaxPrice(
      pair: TradingPair
  )(targetOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]): Option[pair.LimitOrder] = {
    for {
      priceOrders <- targetOrders.lastOption
      bestOrder <- priceOrders._2.headOption // min value has the highest funds
    } yield bestOrder
  }

  /**
   * find the lowest price in the candidate orders
   * in case of two orders with the same price, it will take the one with the higher funds
   */
  private def findMinPrice(
      pair: TradingPair
  )(targetOrders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]): Option[pair.LimitOrder] = {
    for {
      priceOrders <- targetOrders.headOption
      bestOrder <- priceOrders._2.headOption // min value has the highest funds
    } yield bestOrder
  }
}
