package io.stakenet.orderbook.services.validators

import helpers.Helpers
import io.stakenet.orderbook.models.trading.TradingPair.{XSN_BTC, XSN_LTC}
import io.stakenet.orderbook.models.trading.{OrderSide, TradingOrder}
import io.stakenet.orderbook.models.{OrderId, Satoshis}
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class FeesSpec extends AnyWordSpec {
  "getCurrencyPayment" should {
    "work" in pending
  }

  "getFeeAmount" should {
    "produce the correct fee in LTC with buy order" in {
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = Helpers.asSatoshis("0.0094"),
          price = Helpers.asSatoshis("0.0094")
        )
      )

      val expected = Helpers.asSatoshis("0.00000094")
      val result = Fees.getFeeAmount(order)
      result must be(expected)
    }

    "produce the correct fee in BTC with buy order" in {
      val pair = XSN_BTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = Helpers.asSatoshis("0.0094"),
          price = Helpers.asSatoshis("0.0094")
        )
      )

      val expected = Helpers.asSatoshis("0.00000094")
      val result = Fees.getFeeAmount(order)
      result must be(expected)
    }

    "produce the correct fee in XSN with sell order for LTC" in {
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = Helpers.asSatoshis("0.0096"),
          price = Helpers.asSatoshis("0.0094")
        )
      )

      val expected = Helpers.asSatoshis("0.00000096")
      val result = Fees.getFeeAmount(order)
      result must be(expected)
    }

    "produce the correct fee in XSN with sell order for BTC" in {
      val pair = XSN_BTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = Helpers.asSatoshis("0.0098"),
          price = Helpers.asSatoshis("0.0098")
        )
      )

      val expected = Helpers.asSatoshis("0.00000098")
      val result = Fees.getFeeAmount(order)
      result must be(expected)
    }

    "produce the min fee" in {
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = Helpers.asSatoshis("0.0000000000000003"),
          price = Helpers.asSatoshis("0.000000000000000001")
        )
      )
      val expected = Satoshis.One
      val result = Fees.getFeeAmount(order)
      result must be(expected)
    }
  }
}
