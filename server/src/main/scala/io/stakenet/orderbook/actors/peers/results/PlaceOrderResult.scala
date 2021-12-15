package io.stakenet.orderbook.actors.peers.results

import io.stakenet.orderbook.models.trading.{Trade, TradingOrder}

sealed trait PlaceOrderResult extends Product with Serializable

object PlaceOrderResult {
  final case class OrderRejected(reason: String) extends PlaceOrderResult
  final case class OrderMatched(trade: Trade, orderMatchedWith: TradingOrder) extends PlaceOrderResult
  final case class OrderPlaced(order: TradingOrder) extends PlaceOrderResult
}
