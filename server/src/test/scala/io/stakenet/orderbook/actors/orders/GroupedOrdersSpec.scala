package io.stakenet.orderbook.actors.orders

import helpers.Helpers
import io.stakenet.orderbook.models.{OrderId, Satoshis}
import io.stakenet.orderbook.models.trading.{OrderSide, TradingPair}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._

import scala.collection.immutable.{TreeMap, TreeSet}

class GroupedOrdersSpec extends AnyWordSpec {
  val pairs = List(TradingPair.XSN_LTC, TradingPair.XSN_BTC, TradingPair.LTC_BTC)

  "add" should {
    pairs.foreach { pair =>
      s"add a $pair limit buy order" in {
        val groupedOrders = GroupedOrders.empty
        val limitOrder = pair.LimitOrder(
          OrderSide.Buy,
          pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
        )

        val result = groupedOrders.add(pair)(limitOrder)

        val orders = result.availableFor(pair, OrderSide.Sell)
        countOrders(pair)(orders) mustBe 1
      }

      s"add a $pair limit sell order" in {
        val groupedOrders = GroupedOrders.empty
        val limitOrder = pair.LimitOrder(
          OrderSide.Sell,
          pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
        )

        val result = groupedOrders.add(pair)(limitOrder)

        val orders = result.availableFor(pair, OrderSide.Buy)
        countOrders(pair)(orders) mustBe 1
      }

      s"add a $pair orders group them by price" in {
        val orders = List(
          pair.LimitOrder(
            OrderSide.Sell,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
          ),
          pair.LimitOrder(
            OrderSide.Sell,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
          ),
          pair.LimitOrder(
            OrderSide.Sell,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Helpers.asSatoshis("0.00000002"))
          ),
          pair.LimitOrder(
            OrderSide.Sell,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Helpers.asSatoshis("0.00000002"))
          ),
          pair.LimitOrder(
            OrderSide.Sell,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Helpers.asSatoshis("0.00000002"))
          ),
          pair.LimitOrder(
            OrderSide.Buy,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Helpers.asSatoshis("0.00000002"))
          ),
          pair.LimitOrder(
            OrderSide.Buy,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Helpers.asSatoshis("0.00000002"))
          ),
          pair.LimitOrder(
            OrderSide.Buy,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Helpers.asSatoshis("0.00000003"))
          )
        )

        val result = orders.foldLeft(GroupedOrders.empty) { (groupedOrders, order) =>
          groupedOrders.add(pair)(order)
        }

        val sellOrders = result.availableFor(pair, OrderSide.Buy)
        countOrders(pair)(sellOrders) mustBe 5
        sellOrders.get(Satoshis.One).value.size mustBe 2
        sellOrders.get(Helpers.asSatoshis("0.00000002")).value.size mustBe 3

        val buyOrders = result.availableFor(pair, OrderSide.Sell)
        countOrders(pair)(buyOrders) mustBe 3
        buyOrders.get(Helpers.asSatoshis("0.00000002")).value.size mustBe 2
        buyOrders.get(Helpers.asSatoshis("0.00000003")).value.size mustBe 1
      }
    }
  }

  "remove" should {
    pairs.foreach { pair =>
      s"remove a $pair limit buy order" in {
        val groupedOrders = GroupedOrders.empty
        val limitOrder = pair.LimitOrder(
          OrderSide.Buy,
          pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
        )

        val result = groupedOrders.add(pair)(limitOrder).remove(pair)(limitOrder)

        val orders = result.availableFor(pair, OrderSide.Sell)
        countOrders(pair)(orders) mustBe 0
      }

      s"remove a $pair limit sell order" in {
        val groupedOrders = GroupedOrders.empty
        val limitOrder = pair.LimitOrder(
          OrderSide.Sell,
          pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
        )

        val result = groupedOrders.add(pair)(limitOrder).remove(pair)(limitOrder)

        val orders = result.availableFor(pair, OrderSide.Buy)
        countOrders(pair)(orders) mustBe 0
      }

      s"remove a $pair limit sell order removes the price from the map" in {
        val groupedOrders = GroupedOrders.empty
        val limitOrder = pair.LimitOrder(
          OrderSide.Sell,
          pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
        )

        val result = groupedOrders.add(pair)(limitOrder).remove(pair)(limitOrder)

        val orders = result.availableFor(pair, OrderSide.Buy)
        orders.headOption mustBe empty
      }

      s"remove a $pair limit buy order removes the price from the map" in {
        val groupedOrders = GroupedOrders.empty
        val limitOrder = pair.LimitOrder(
          OrderSide.Buy,
          pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
        )

        val result = groupedOrders.add(pair)(limitOrder).remove(pair)(limitOrder)

        val orders = result.availableFor(pair, OrderSide.Sell)
        orders.headOption mustBe empty
      }
    }
  }

  "availableFor" should {
    pairs.foreach { pair =>
      s"return empty map for $pair when there are no orders" in {
        val groupedOrders = GroupedOrders.empty

        groupedOrders.availableFor(pair, OrderSide.Buy).size mustBe 0
        groupedOrders.availableFor(pair, OrderSide.Sell).size mustBe 0
      }
      s"return empty map for $pair when there are orders from other pairs" in {
        val otherPairs = pairs.filter(_ != pair)

        val groupedOrders = otherPairs.foldLeft(GroupedOrders.empty) { (groupedOrders, otherPair) =>
          val buyOrder = otherPair.LimitOrder(
            OrderSide.Buy,
            otherPair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
          )

          val sellOrder = otherPair.LimitOrder(
            OrderSide.Sell,
            otherPair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
          )

          groupedOrders.add(otherPair)(buyOrder).add(otherPair)(sellOrder)
        }

        groupedOrders.availableFor(pair, OrderSide.Buy).size mustBe 0
        groupedOrders.availableFor(pair, OrderSide.Sell).size mustBe 0
      }

      s"return $pair orders" in {
        val otherPairs = pairs.filter(_ != pair)

        val groupedOrders = otherPairs.foldLeft(GroupedOrders.empty) { (groupedOrders, otherPair) =>
          val buyOrders = List.fill(3)(
            otherPair.LimitOrder(
              OrderSide.Buy,
              otherPair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
            )
          )

          val sellOrders = List.fill(3)(
            otherPair.LimitOrder(
              OrderSide.Sell,
              otherPair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
            )
          )

          val orders = buyOrders ++ sellOrders
          orders.foldLeft(groupedOrders) { (groupedOrders, order) =>
            groupedOrders.add(otherPair)(order)
          }
        }

        val buyOrders = List.fill(5)(
          pair.LimitOrder(
            OrderSide.Buy,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
          )
        )

        val sellOrders = List.fill(4)(
          pair.LimitOrder(
            OrderSide.Sell,
            pair.LimitOrderDetails(OrderId.random(), Satoshis.One, Satoshis.One)
          )
        )

        val orders = buyOrders ++ sellOrders
        val result = orders.foldLeft(groupedOrders) { (groupedOrders, order) =>
          groupedOrders.add(pair)(order)
        }

        countOrders(pair)(result.availableFor(pair, OrderSide.Buy)) mustBe 4
        countOrders(pair)(result.availableFor(pair, OrderSide.Sell)) mustBe 5
      }
    }
  }

  private def countOrders(pair: TradingPair)(orders: TreeMap[Satoshis, TreeSet[pair.LimitOrder]]): Int = {
    orders.foldLeft(0) {
      case (count, (_, orders)) =>
        count + orders.size
    }
  }
}
