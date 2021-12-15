package io.stakenet.orderbook.models.trading

trait TradingOrder {
  val pair: TradingPair
  val value: pair.Order

  def fold[A](onLimitOrder: pair.LimitOrder => A, onMarketOrder: pair.MarketOrder => A): A = {
    value match {
      case x: pair.LimitOrder => onLimitOrder(x)
      case x: pair.MarketOrder => onMarketOrder(x)
    }
  }

  def asLimitOrder: Option[pair.LimitOrder] = value match {
    case x: pair.LimitOrder => Some(x)
    case _ => None
  }

  def asMarketOrder: Option[pair.MarketOrder] = value match {
    case x: pair.MarketOrder => Some(x)
    case _ => None
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: TradingOrder => value == that.value
    case _ => false
  }
  override def toString: String = value.toString
}

object TradingOrder {

  def apply(_pair: TradingPair)(_order: _pair.Order): TradingOrder = new TradingOrder {
    override val pair: _pair.type = _pair
    override val value: pair.Order = _order
  }

  def apply(order: TradingPair#Order): TradingOrder = {
    val pair = order.tradingPair
    apply(pair)(pair.use(order).getOrElse(throw new RuntimeException("Impossible")))
  }
}
