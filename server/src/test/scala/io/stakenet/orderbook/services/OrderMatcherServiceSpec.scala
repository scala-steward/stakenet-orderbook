package io.stakenet.orderbook.services

import helpers.Helpers
import io.stakenet.orderbook.helpers.CustomMatchers
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.trading.{OrderSide, Trade, TradingOrderMatching, TradingPair}
import io.stakenet.orderbook.services.impl.{SimpleOrderMatcherService, TreeBasedOrderMatcherService}
import org.scalatest.wordspec.AnyWordSpec

class OrderMatcherServiceSpec extends AnyWordSpec {

  import TradingPair._

  val simpleService = new SimpleOrderMatcherService
  val treeService = new TreeBasedOrderMatcherService
  val services = List("simple" -> simpleService, "tree" -> treeService)

  def doTest(tag: String, service: OrderMatcherService, input: TradingOrderMatching, expected: Option[Trade]): Unit = {
    val result = service.matchOrder(input)
    (expected, result) match {
      case (Some(a), Some(b)) =>
        try {
          val _ = CustomMatchers.matchTrades(a, b)
        } catch {
          case ex: Throwable =>
            println(s"$tag - Trade matching failed:")
            println(s"a: $a")
            println(s"b: $b")
            throw ex
        }
      case (None, None) => ()
      case _ => fail(s"$tag failed- expected ${expected} but got ${result}")
    }
  }

  private def doTest(input: TradingOrderMatching, expected: Option[Trade]): Unit = {
    services.foreach {
      case (name, service) =>
        doTest(name, service, input, expected)
    }
  }

  "matchOrder" should {

    def randomOrder(pair: TradingPair, side: OrderSide): pair.LimitOrder = {
      pair.Order.limit(
        side = side,
        id = OrderId.random(),
        funds = Helpers.randomSatoshis(),
        price = Helpers.randomSatoshis()
      )
    }

    def debugOrder(order: TradingPair#LimitOrder): String = {
      s"""${order.pair}.Order.limit(
         |  side = OrderSide.${order.side},
         |  id = OrderId.from("${order.id.value.toString}").value,
         |  funds = Satoshis.from(BigInt(${order.details.funds.value(Satoshis.Digits)}, ${Satoshis.Digits})).value,
         |  price = Satoshis.from(BigInt(${order.details.price.value(Satoshis.Digits)}, ${Satoshis.Digits}})).value
         |)""".stripMargin
    }
    def debugOrders(orders: List[TradingPair#LimitOrder]): String = {
      orders
        .map(debugOrder)
        .mkString("List(", ",\n", ")")
    }

    "work on random tests" in {
      (1 to 100).foreach { _ =>
        val orders = (1 to 10000).map(_ => randomOrder(XSN_BTC, OrderSide.Buy)).toList
        val candidate = randomOrder(XSN_BTC, OrderSide.Sell)
        val input = TradingOrderMatching.fromList(XSN_BTC)(candidate, orders)
        val expected = simpleService.matchOrder(input)
        try {
          doTest("sample", treeService, input, expected)
        } catch {
          case ex: Throwable =>
            println("Test failed")
            println(s"val candidate = ${debugOrder(candidate)}")
            println(s"val orders = ${debugOrders(orders)}")
            println("""val input = TradingOrderMatching.fromList(XSN_BTC)(candidate, orders)""")
            println("""val a = simpleService.matchOrder(input)""")
            println("""val b = treeService.matchOrder(input)""")
            println("""println(a)""")
            println("""println(b)""")
            throw ex
        }
      }
    }

    // sell 100 XSN for LTC at the market price
    val XSN_LTC_SELL_MARKET = XSN_LTC.Order.market(OrderSide.Sell, OrderId.random(), funds = getSatoshis(100))
    val XSN_LTC_BUY_LIMIT =
      XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(2000000))

    "find no match when there aren't available orders" in {
      val input = TradingOrderMatching.fromList(XSN_LTC)(XSN_LTC_SELL_MARKET, List.empty)
      doTest(input = input, expected = None)
    }

    "find no match when placing a sell limit order but there aren't buy orders with equal or higher price" in {
      val order =
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(2000000))
      val available = List(
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(1999950)),
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(1999999))
      )

      val input = TradingOrderMatching.fromList(XSN_LTC)(order, available)
      doTest(input = input, expected = None)
    }

    "match a sell limit order when there are buy orders with higher price" in {
      val order =
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(2000000))
      val available = List(
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(2000010)),
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(2000008))
      )
      val expected = Trade.from(XSN_LTC)(order, available.head)

      val input = TradingOrderMatching.fromList(XSN_LTC)(order, available)
      doTest(input = input, expected = Some(expected))
    }

    "match a buy limit order when there are sell orders with lower price" in {
      val order =
        XSN_BTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(2000000))
      val available = List(
        XSN_BTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(1900000)),
        XSN_BTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(1800000))
      )
      val expected = Trade.from(XSN_BTC)(order, available(1))

      val input = TradingOrderMatching.fromList(XSN_BTC)(order, available)
      doTest(input = input, expected = Some(expected))
    }

    "match a market order with a single limit order" in {
      val input = TradingOrderMatching.fromList(XSN_LTC)(XSN_LTC_SELL_MARKET, List(XSN_LTC_BUY_LIMIT))
      val expected = Trade.from(XSN_LTC)(XSN_LTC_SELL_MARKET, XSN_LTC_BUY_LIMIT)
      doTest(input = input, expected = Some(expected))
    }

    "match a buy market order with the lowest limit-priced sell order" in {
      val order = XSN_LTC.Order.market(OrderSide.Buy, OrderId.random(), getSatoshis(100))
      val available = List(
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(5000000001L)),
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(4999999999L)),
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(6000000000L))
      )
      val input = TradingOrderMatching.fromList(XSN_LTC)(order, available)
      val expected = Trade.from(XSN_LTC)(order, available(1))
      doTest(input = input, expected = Some(expected))
    }

    "match a sell market order with the highest limit-priced buy order" in {
      val order = XSN_LTC.Order.market(OrderSide.Sell, OrderId.random(), getSatoshis(100))
      val available = List(
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(5000000001L)),
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(4999999999L)),
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(6000000000L))
      )
      val input = TradingOrderMatching.fromList(XSN_LTC)(order, available)
      val expected = Trade.from(XSN_LTC)(order, available(2))
      doTest(input = input, expected = Some(expected))
    }

    "match a limit order with the highest amount order matching the price" in {
      val order =
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(100), price = getSatoshis(2000000))
      val available = List(
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(1), price = getSatoshis(2000000)),
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(10), price = getSatoshis(2000000))
      )

      val input = TradingOrderMatching.fromList(XSN_LTC)(order, available)
      val expected = Trade.from(XSN_LTC)(order, available(1))
      doTest(input = input, expected = Some(expected))
    }

    "match a limit sell order with the highest amount order matching the price" in {
      val order = XSN_LTC.Order.limit(
        OrderSide.Sell,
        OrderId.random(),
        funds = getSatoshis(100),
        price = getSatoshis(2000000)
      )

      val orders = List(
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = Satoshis.One, price = getSatoshis(2000000)),
        XSN_LTC.Order.limit(OrderSide.Buy, OrderId.random(), funds = getSatoshis(10), price = getSatoshis(2000000))
      )

      val input = TradingOrderMatching.fromList(XSN_LTC)(order, orders)
      val expected = Trade.from(XSN_LTC)(order, orders.last)
      doTest(input = input, expected = Some(expected))
    }

    "match a limit buy order with the highest amount order matching the price" in {
      val order = XSN_LTC.Order.limit(
        OrderSide.Buy,
        OrderId.random(),
        funds = getSatoshis(100),
        price = getSatoshis(2000000)
      )

      val orders = List(
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = Satoshis.One, price = getSatoshis(2000000)),
        XSN_LTC.Order.limit(OrderSide.Sell, OrderId.random(), funds = getSatoshis(10), price = getSatoshis(2000000))
      )

      val input = TradingOrderMatching.fromList(XSN_LTC)(order, orders)
      val expected = Trade.from(XSN_LTC)(order, orders.last)
      doTest(input = input, expected = Some(expected))
    }
  }
}
