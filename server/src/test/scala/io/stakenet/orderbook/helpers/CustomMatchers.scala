package io.stakenet.orderbook.helpers

import io.stakenet.orderbook.models.trading.{Trade, TradingOrder}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers._

object CustomMatchers {

  // The id and executedOn fields are auto-generated, hence, they won't be equal
  def matchTrades(expected: Trade, actual: Trade) = {
    actual.pair must be(expected.pair)
    actual.price must be(expected.price)
    actual.size must be(expected.size)
    actual.existingOrder must be(expected.existingOrder)
    actual.executingOrder must be(expected.executingOrder)
    actual.executingOrderSide must be(expected.executingOrderSide)
  }

  // the id is auto generated, it won't be equal
  def matchOrderIgnoreId(expected: TradingOrder, actual: TradingOrder): Assertion = {
    expected.value.tradingPair mustBe actual.value.tradingPair
    expected.value.side mustBe actual.value.side
    expected.value.feePercent mustBe actual.value.feePercent
    expected.value.feeCurrency mustBe actual.value.feeCurrency
    expected.value.sellingCurrency mustBe actual.value.sellingCurrency
    expected.value.buyingCurrency mustBe actual.value.buyingCurrency
    expected.value.funds mustBe actual.value.funds
    (expected.asLimitOrder, actual.asLimitOrder) match {
      case (Some(expectedValue), Some(actualValue)) =>
        expectedValue.details.price mustBe actualValue.details.price
      case (None, None) => succeed
      case (Some(_), None) => fail("The expected order is a limit order and the actual is a market order")
      case (None, Some(_)) => fail("The expected order is a market order and the actual is a limit order")
    }
  }
}
