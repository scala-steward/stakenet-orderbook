package io.stakenet.orderbook.actors.peers

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import io.stakenet.orderbook.actors.orders.PeerOrder
import io.stakenet.orderbook.helpers.SampleOrders
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.Trade
import io.stakenet.orderbook.models.trading.TradingPair.{XSN_BTC, XSN_LTC}
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class PeerStateSpec extends TestKit(ActorSystem("PeerStateSpec")) with AnyWordSpecLike with BeforeAndAfterAll {

  private val order = SampleOrders.XSN_BTC_BUY_LIMIT_1
  private val orderMatched = SampleOrders.XSN_BTC_SELL_LIMIT_1
  private val peerMatched = TestProbe().ref

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "add" should {
    "add an order" in {
      val state = PeerState.empty
      val result = state.add(order)

      result.findOpenOrder(order.value.id).value must be(order)
    }
  }

  "cancelOpenOrder" should {
    "remove an order" in {
      val state = PeerState.empty
      val result = state.add(order)
      val removed = result.cancelOpenOrder(order.value.id)

      removed.findOpenOrder(order.value.id) must be(empty)
    }

    "do nothing if the order to remove is not in my list" in {
      val state = PeerState.empty
      val result = state.add(order)
      val removed = result.cancelOpenOrder(orderMatched.value.id)

      removed.findOpenOrder(orderMatched.value.id) must be(empty)
      removed.findOpenOrder(order.value.id).value must be(order)
    }
  }

  "removeOrders" should {
    "remove all open orders for a trading pair" in {
      val order2 = SampleOrders.XSN_LTC_BUY_LIMIT_1
      val state = PeerState.empty
      val result = state.add(order).add(order2)
      val removed = result.removeOrders(order.pair)

      removed.findOpenOrder(order2.value.id).value must be(order2)
      removed.findOpenOrder(order.value.id) must be(empty)
    }

    "remove all matched orders for a trading pair" in {
      val pair = XSN_BTC
      val state = PeerState.empty.add(order)
      val orderTrade = Trade.from(pair)(pair.use(order.value).value, pair.useLimitOrder(orderMatched.value).value)

      val order2 = SampleOrders.XSN_LTC_BUY_LIMIT_1
      val orderMatched2 = SampleOrders.XSN_LTC_SELL_LIMIT_1
      val orderTrade2 =
        Trade.from(XSN_LTC)(XSN_LTC.use(order2.value).value, XSN_LTC.useLimitOrder(orderMatched2.value).value)

      val peerOrder1 = PeerOrder(ClientId.random(), peerMatched, order)
      val peerOrder2 = PeerOrder(ClientId.random(), peerMatched, order2)
      val result = state.`match`(orderTrade, peerOrder1).`match`(orderTrade2, peerOrder2)
      val removed = result.removeOrders(pair)
      val expected = List(PeerTrade(orderTrade2, peerOrder2))

      removed.matched must be(expected)

    }
  }

  "match" should {
    "match my existing order and move it to matched" in {
      val pair = XSN_BTC
      val state = PeerState.empty.add(order)
      val orderPair = Trade.from(pair)(pair.use(order.value).value, pair.useLimitOrder(orderMatched.value).value)
      val peerOrder = PeerOrder(ClientId.random(), peerMatched, order)
      val result = state.`match`(orderPair, peerOrder)
      val expected = List(PeerTrade(orderPair, peerOrder))

      result.matched must be(expected)
      result.findOpenOrder(order.value.id) must be(empty)
    }

    "match my new order and add it to matched" in {
      val pair = XSN_BTC
      val state = PeerState.empty.add(order)
      val orderPair = Trade.from(pair)(pair.use(order.value).value, pair.useLimitOrder(orderMatched.value).value)
      val peerOrder = PeerOrder(ClientId.random(), peerMatched, order)
      val result = state.`match`(orderPair, peerOrder)
      val expected = List(PeerTrade(orderPair, peerOrder))

      result.matched must be(expected)
      result.findOpenOrder(order.value.id) must be(empty)
    }

    "cancel a matched order must remove the orders" in {
      val pair = XSN_BTC
      val pair2 = XSN_LTC
      val order2 = SampleOrders.XSN_LTC_BUY_LIMIT_1
      val orderMatched2 = SampleOrders.XSN_LTC_SELL_LIMIT_1

      val state = PeerState.empty.add(order).add(order2)
      val orderPair = Trade.from(pair)(pair.use(order.value).value, pair.useLimitOrder(orderMatched.value).value)
      val orderPair2 = Trade.from(pair2)(pair2.use(order2.value).value, pair2.useLimitOrder(orderMatched2.value).value)

      val peerOrder1 = PeerOrder(ClientId.random(), peerMatched, order)
      val peerOrder2 = PeerOrder(ClientId.random(), peerMatched, order2)
      val result = state.`match`(orderPair, peerOrder1).`match`(orderPair2, peerOrder2).removeMatched(order.value.id)

      val expected = List(PeerTrade(orderPair2, peerOrder2))

      result.matched must be(expected)
      result.findOpenOrder(order.value.id) must be(empty)
      result.findOpenOrder(order2.value.id) must be(empty)
    }

    "swap complete " in {
      val pair = XSN_BTC
      val order2 = SampleOrders.XSN_LTC_BUY_LIMIT_1

      val state = PeerState.empty.add(order).add(order2)
      val orderPair = Trade.from(pair)(pair.use(order.value).value, pair.useLimitOrder(orderMatched.value).value)
      val peerOrder = PeerOrder(ClientId.random(), peerMatched, order)
      val peerTrade = PeerTrade(orderPair, peerOrder)
      val result = state.`match`(orderPair, peerOrder).removeTrade(peerTrade.trade.id)

      result.matched must be(List.empty)
    }
  }
}
