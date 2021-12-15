package io.stakenet.orderbook.services

import io.stakenet.orderbook.models.trading.{Trade, TradingOrderMatching}

trait OrderMatcherService {

  def matchOrder(orderMatching: TradingOrderMatching): Option[Trade]
}
