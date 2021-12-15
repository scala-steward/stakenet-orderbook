package io.stakenet.orderbook.models.trading

import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.{OrderId, Satoshis}
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class TradeSpec extends AnyWordSpec {
  "from" should {
    "construct a trade object with executing buy order" in {
      val pair = TradingPair.XSN_BTC

      val existingPriceBTC: Satoshis = getSatoshis(900)
      val existingFundsXSN = getSatoshis(2000)
      val existingOrder = pair.Order.limit(
        OrderSide.Sell,
        OrderId.random(),
        funds = existingFundsXSN,
        price = existingPriceBTC
      )

      val executingFundsBTC: Satoshis = getSatoshis(1500)
      val executingFundsXSN = executingFundsBTC / existingPriceBTC
      val executingOrder = pair.Order.market(OrderSide.Buy, OrderId.random(), executingFundsBTC)

      val expectedValue = executingFundsXSN min existingFundsXSN // the order with lower size is what can be traded
      val trade = Trade.from(pair)(executingOrder, existingOrder)
      trade.executingOrder must be(executingOrder.id)
      trade.existingOrder must be(existingOrder.id)
      trade.price must be(existingOrder.details.price)
      trade.size must be(expectedValue)
      trade.executingOrderSide must be(OrderSide.Buy)
    }

    "construct a trade object with existing buy order" in {
      val pair = TradingPair.XSN_BTC

      val existingPriceBTC: Satoshis = getSatoshis(50000000)
      val existingFundsBTC = getSatoshis(200000000)
      val sizeXSN = existingFundsBTC / existingPriceBTC

      val existingOrder = pair.Order.limit(
        OrderSide.Buy,
        OrderId.random(),
        funds = existingFundsBTC,
        price = existingPriceBTC
      )
      val executingFundsXSN: Satoshis = getSatoshis(1500)
      val executingOrder = pair.Order.market(OrderSide.Sell, OrderId.random(), executingFundsXSN)
      val expectedValue = executingFundsXSN min sizeXSN // the order with lower size is what can be traded
      val trade = Trade.from(pair)(executingOrder, existingOrder)
      trade.executingOrder must be(executingOrder.id)
      trade.existingOrder must be(existingOrder.id)
      trade.price must be(existingOrder.details.price)
      trade.size must be(expectedValue)
      trade.executingOrderSide must be(OrderSide.Sell)
    }

    "construct a trade object with executing buy order,  both limit" in {
      val pair = TradingPair.XSN_BTC

      val existingPriceBTC: Satoshis = getSatoshis(900)
      val existingFundsXSN = getSatoshis(2000)
      val existingOrder = pair.Order.limit(
        OrderSide.Sell,
        OrderId.random(),
        funds = existingFundsXSN,
        price = existingPriceBTC
      )

      val executingFundsBTC: Satoshis = getSatoshis(1500)
      val executingFundsXSN = executingFundsBTC / existingPriceBTC
      val executingOrder = pair.Order.market(OrderSide.Buy, OrderId.random(), executingFundsBTC)

      val expectedValue = executingFundsXSN min existingFundsXSN // the order with lower size is what can be traded
      val trade = Trade.from(pair)(executingOrder, existingOrder)
      trade.executingOrder must be(executingOrder.id)
      trade.existingOrder must be(existingOrder.id)
      trade.price must be(existingOrder.details.price)
      trade.size must be(expectedValue)
      trade.executingOrderSide must be(OrderSide.Buy)
    }

    "construct a trade object with existing buy order, both limit" in {
      val pair = TradingPair.XSN_BTC

      val existingPriceBTC: Satoshis = getSatoshis(800)
      val existingFundsBTC = getSatoshis(2000)
      val existingFundsXSN = existingFundsBTC / existingPriceBTC
      val existingOrder = pair.Order.limit(
        OrderSide.Buy,
        OrderId.random(),
        funds = existingFundsBTC,
        price = existingPriceBTC
      )
      val executingPriceBTC: Satoshis = getSatoshis(750)
      val executingFundsXSN: Satoshis = getSatoshis(1500)
      val executingOrder =
        pair.Order.limit(OrderSide.Sell, OrderId.random(), executingFundsXSN, price = executingPriceBTC)

      val expectedValue = executingFundsXSN min existingFundsXSN // the order with lower funds is what can be traded
      val trade = Trade.from(pair)(executingOrder, existingOrder)
      trade.executingOrder must be(executingOrder.id)
      trade.existingOrder must be(existingOrder.id)
      trade.price must be(existingOrder.details.price)
      trade.size must be(expectedValue)
      trade.executingOrderSide must be(OrderSide.Sell)
    }

    "construct a trade object matched by a sell order" in {
      val pair = TradingPair.XSN_BTC
      val firstFundsBTC: Satoshis = getSatoshis(742)
      val first = pair.Order.market(OrderSide.Sell, OrderId.random(), firstFundsBTC)

      val secondPriceBTC: Satoshis = getSatoshis(900)
      val secondFundsXSN = getSatoshis(2879)
      val secondFundsBTC = secondFundsXSN * secondPriceBTC
      val second = pair.Order.limit(OrderSide.Buy, OrderId.random(), funds = secondFundsBTC, price = secondPriceBTC)

      val trade = Trade.from(pair)(first, second)
      trade.executingOrderSide must be(OrderSide.Sell)
    }

    "construct a trade with correct fee amounts" in {
      val pair = TradingPair.XSN_BTC
      val firstFundsXSN: Satoshis = getSatoshis(50000)

      val executingOrder = pair.Order.market(OrderSide.Sell, OrderId.random(), firstFundsXSN)

      val priceXSN: Satoshis = getSatoshis(2)
      val existingFundsBTC = getSatoshis(20000)
      val existingOrder =
        pair.Order.limit(OrderSide.Buy, OrderId.random(), funds = existingFundsBTC, price = priceXSN)

      val trade = Trade.from(pair)(executingOrder, existingOrder)
      trade.buyOrderFunds must be(existingFundsBTC)
      trade.buyOrderFee must be(getSatoshis(2))
      trade.sellOrderFee must be(getSatoshis(1))
    }

    "fail if both orders are on the buy side" in {
      val pair = TradingPair.XSN_BTC
      val first = pair.Order.market(OrderSide.Buy, OrderId.random(), funds = getSatoshis(100))
      val second = pair.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(10))
      intercept[Exception] {
        Trade.from(pair)(first, second)
      }
    }

    "fail if both orders are on the sell side" in {
      val pair = TradingPair.XSN_BTC
      val first = pair.Order.market(OrderSide.Sell, OrderId.random(), getSatoshis(100))
      val second = pair.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(10))
      intercept[Exception] {
        Trade.from(pair)(first, second)
      }
    }
  }
}
