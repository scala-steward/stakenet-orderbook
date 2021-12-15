package io.stakenet.orderbook.models.trading

import helpers.Helpers
import io.stakenet.orderbook.models.OrderId
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class LimitOrderOrderingSpec extends AnyWordSpec {
  val pair = TradingPair.XSN_BTC
  val ordering = pair.Order.orderingBySmallerPriceHigherFunds

  "orderingBySmallerPriceHigherFunds" should {
    "get the smaller order by price" in {
      val a = pair.Order.limit(
        side = OrderSide.Buy,
        id = OrderId.random(),
        price = Helpers.asSatoshis("0.0000001"),
        funds = Helpers.asSatoshis("0.0000001")
      )
      val b = pair.Order.limit(
        side = OrderSide.Buy,
        id = OrderId.random(),
        price = Helpers.asSatoshis("0.00000005"),
        funds = Helpers.asSatoshis("0.0000001")
      )

      List(a, b).sorted(ordering) must be(List(b, a))
    }

    "get the smaller order by higher funds when the price is the same" in {
      val a = pair.Order.limit(
        side = OrderSide.Buy,
        id = OrderId.random(),
        price = Helpers.asSatoshis("0.0000001"),
        funds = Helpers.asSatoshis("0.0000001")
      )
      val b = pair.Order.limit(
        side = OrderSide.Buy,
        id = OrderId.random(),
        price = Helpers.asSatoshis("0.0000001"),
        funds = Helpers.asSatoshis("0.00000005")
      )

      List(a, b).sorted(ordering) must be(List(a, b))
    }

    "get the smaller order by smaller id when there price and funds are the same" in {
      val ids = List(OrderId.random(), OrderId.random()).sorted
      val a = pair.Order.limit(
        side = OrderSide.Buy,
        id = ids.head,
        price = Helpers.asSatoshis("0.0000001"),
        funds = Helpers.asSatoshis("0.0000001")
      )
      val b = pair.Order.limit(
        side = OrderSide.Buy,
        id = ids(1),
        price = Helpers.asSatoshis("0.0000001"),
        funds = Helpers.asSatoshis("0.0000001")
      )

      List(a, b).sorted(ordering) must be(List(a, b))
    }
  }
}
