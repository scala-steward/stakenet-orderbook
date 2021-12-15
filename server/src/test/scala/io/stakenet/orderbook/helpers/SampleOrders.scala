package io.stakenet.orderbook.helpers

import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.trading.{OrderSide, TradingOrder, TradingPair}
import org.scalatest.OptionValues._

import scala.language.implicitConversions

object SampleOrders {

  import TradingPair._

  def XSN_BTC_BUY_MARKET: TradingOrder = XSN_BTC.Order.market(OrderSide.Buy, OrderId.random(), getSatoshis(100))
  def XSN_BTC_SELL_MARKET: TradingOrder = XSN_BTC.Order.market(OrderSide.Sell, OrderId.random(), getSatoshis(100))

  def XSN_BTC_BUY_LIMIT_1: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(100))

  def XSN_BTC_SELL_LIMIT_1: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(100))

  def XSN_BTC_BUY_LIMIT_2: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(200))

  def XSN_BTC_SELL_LIMIT_2: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(200))

  def XSN_BTC_BUY_LIMIT_3: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(50))

  def XSN_BTC_BUY_LIMIT_4: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(10), price = getSatoshis(100))

  def XSN_BTC_SELL_LIMIT_3: TradingOrder =
    XSN_BTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(500))

  def XSN_LTC_BUY_MARKET: TradingOrder = XSN_LTC.Order.market(OrderSide.Buy, OrderId.random(), getSatoshis(100))
  def XSN_LTC_SELL_MARKET: TradingOrder = XSN_LTC.Order.market(OrderSide.Sell, OrderId.random(), getSatoshis(100))

  def XSN_LTC_BUY_LIMIT_1: TradingOrder =
    XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(100))

  def XSN_LTC_SELL_LIMIT_1: TradingOrder =
    XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(100))

  def XSN_LTC_BUY_LIMIT_2: TradingOrder =
    XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(200))

  def XSN_LTC_SELL_LIMIT_2: TradingOrder =
    XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(200))

  def XSN_LTC_BUY_LIMIT_3: TradingOrder =
    XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(150))

  def XSN_LTC_SELL_LIMIT_3: TradingOrder =
    XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(150))

  implicit def toTradingOrder(order: TradingPair#Order): TradingOrder = {
    val pair = order.tradingPair
    order match {
      case x: pair.Order => TradingOrder(pair)(x)
      case _ => throw new RuntimeException("Impossible")
    }
  }

  def getSatoshis(number: Long): Satoshis = {
    Satoshis.from(BigDecimal(number)).value
  }

  def getSatoshis(number: Int): Satoshis = {
    Satoshis.from(BigDecimal(number)).value
  }

  def buyLimitOrder(
      pair: TradingPair,
      funds: Satoshis = getSatoshis(100),
      price: Satoshis = getSatoshis(100)
  ): pair.LimitOrder = {
    pair.Order.limit(OrderSide.Buy, OrderId.random(), funds, price)
  }

  def sellLimitOrder(
      pair: TradingPair,
      funds: Satoshis = getSatoshis(100),
      price: Satoshis = getSatoshis(100)
  ): pair.LimitOrder = {
    pair.Order.limit(OrderSide.Sell, OrderId.random(), funds, price)
  }

  def buyMarketOrder(
      pair: TradingPair,
      funds: Satoshis = getSatoshis(100)
  ): pair.MarketOrder = {
    pair.Order.market(OrderSide.Buy, OrderId.random(), funds)
  }

  def sellMarketOrder(
      pair: TradingPair,
      funds: Satoshis = getSatoshis(100)
  ): pair.MarketOrder = {
    pair.Order.market(OrderSide.Sell, OrderId.random(), funds)
  }
}
