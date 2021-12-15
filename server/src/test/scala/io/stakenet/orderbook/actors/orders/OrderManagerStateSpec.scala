package io.stakenet.orderbook.actors.orders

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import io.stakenet.orderbook.helpers.SampleOrders
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.OrderSide
import io.stakenet.orderbook.models.trading.TradingPair.{XSN_BTC, XSN_LTC}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

class OrderManagerStateSpec
    extends TestKit(ActorSystem("OrderManagerStateSpec"))
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  val order = SampleOrders.XSN_LTC_BUY_LIMIT_1
  val peer = TestProbe().ref

  val peerOrder = PeerOrder(ClientId.random(), peer, order)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "add" should {
    "add a peer order" in {
      val state = OrderManagerState.empty
        .add(peerOrder)

      state.find(peerOrder.order.value.id).value must be(peerOrder)
    }

    "add order to GroupedOrders" in {
      val state = OrderManagerState.empty
        .add(peerOrder)

      val orders = state.groupedOrders
        .availableFor(XSN_LTC, OrderSide.Sell)
        .values
        .foldLeft(List.empty[XSN_LTC.LimitOrder])((orders, priceOrders) => orders ::: priceOrders.toList)

      val expected = List(peerOrder.order.value)
      orders mustBe expected
    }
  }

  "find by id" should {
    "return the order" in {
      val state = OrderManagerState.empty
        .add(peerOrder)

      state.find(peerOrder.order.value.id).value must be(peerOrder)
    }

    "return no result" in {
      val state = OrderManagerState.empty
        .add(peerOrder)

      state.find(OrderId.random()) must be(empty)
    }
  }

  "find by owner" should {
    "return the order" in {
      val otherPeer = TestProbe().ref
      val otherOrder1 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder2 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherOrder1)
        .add(otherOrder2)

      state.find(otherPeer) must be(Set(otherOrder1, otherOrder2))
    }

    "return no result" in {
      val otherPeer = TestProbe().ref
      val otherOrder1 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder2 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherOrder1)
        .add(otherOrder2)

      state.find(TestProbe().ref) must be(empty)
    }
  }

  "find by id and owner" should {
    "return the order" in {
      val otherPeer = TestProbe().ref
      val otherOrder1 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder2 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherOrder1)
        .add(otherOrder2)

      state.find(otherOrder1.order.value.id, otherPeer).value must be(otherOrder1)
    }

    "return no result" in {
      val otherPeer = TestProbe().ref
      val otherOrder1 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder2 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherOrder1)
        .add(otherOrder2)

      state.find(otherOrder1.order.value.id, peer) must be(empty)
    }
  }

  "find by owner and pair" should {
    "return the order" in {
      val otherPeer = TestProbe().ref
      val otherOrder1 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder2 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder3 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_LTC_BUY_LIMIT_1)
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherOrder1)
        .add(otherOrder2)
        .add(otherOrder3)

      state.find(otherPeer, XSN_BTC) must be(Set(otherOrder1, otherOrder2))
    }

    "return no result" in {
      val otherPeer = TestProbe().ref
      val otherOrder1 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val otherOrder2 = PeerOrder(ClientId.random(), otherPeer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherOrder1)
        .add(otherOrder2)

      state.find(otherPeer, XSN_LTC) must be(empty)
    }
  }

  "remove by order" should {
    "remove an order" in {
      val state = OrderManagerState.empty
        .add(peerOrder)
        .remove(peerOrder)

      state.find(peerOrder.order.value.id) must be(empty)
    }

    "remove an order from GroupedOrders" in {
      val state = OrderManagerState.empty
        .add(peerOrder)
        .remove(peerOrder)

      val orders = state.groupedOrders
        .availableFor(XSN_LTC, OrderSide.Sell)
        .values
        .foldLeft(List.empty[XSN_LTC.LimitOrder])((orders, priceOrders) => orders ::: priceOrders.toList)

      orders mustBe empty
    }
  }

  "remove by peer" should {
    "remove all orders from a peer" in {
      val peerOrder2 = SampleOrders.XSN_BTC_BUY_LIMIT_1

      val otherPeer = TestProbe().ref
      val otherOrder = SampleOrders.XSN_BTC_SELL_LIMIT_1
      val otherPeerOrder = PeerOrder(ClientId.random(), otherPeer, otherOrder)

      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(otherPeerOrder)
        .add(PeerOrder(ClientId.random(), peer, peerOrder2))
        .remove(peerOrder.peer)

      state.find(peerOrder.order.value.id) must be(empty)
      state.find(peerOrder2.value.id) must be(empty)
      state.find(otherOrder.value.id).value must be(otherPeerOrder)
    }

    "remove all subscriptions from a peer" in {
      val peerTwo = TestProbe().ref
      val state = OrderManagerState.empty
        .subscribe(XSN_BTC, peerOrder.peer)
        .subscribe(XSN_LTC, peerOrder.peer)
        .subscribe(XSN_BTC, peerTwo)

      val result = state.remove(peerOrder.peer)
      result.subscriptors(XSN_BTC) must be(Set(peerTwo))
      result.subscriptors(XSN_LTC) must be(empty)
    }

    "remove all orders from a peer from GroupedOrders" in {
      val peerTwo = TestProbe().ref
      val orderTwo = SampleOrders.XSN_LTC_BUY_LIMIT_2
      val peerOrderTwo = PeerOrder(ClientId.random(), peerTwo, orderTwo)

      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(peerOrderTwo)
        .add(PeerOrder(ClientId.random(), peer, SampleOrders.XSN_LTC_BUY_LIMIT_3))
        .remove(peerOrder.peer)

      val orders = state.groupedOrders
        .availableFor(XSN_LTC, OrderSide.Sell)
        .values
        .foldLeft(List.empty[XSN_LTC.LimitOrder])((orders, priceOrders) => orders ::: priceOrders.toList)

      val expected = List(peerOrderTwo.order.value)
      orders mustBe expected
    }
  }

  "remove by pair" should {
    "remove all pair orders from a peer" in {
      val order1 = PeerOrder(ClientId.random(), peer, SampleOrders.XSN_LTC_SELL_LIMIT_1)
      val order2 = PeerOrder(ClientId.random(), peer, SampleOrders.XSN_BTC_BUY_LIMIT_1)
      val order3 = PeerOrder(ClientId.random(), peer, SampleOrders.XSN_BTC_SELL_LIMIT_2)
      val peerOrderTwo = PeerOrder(ClientId.random(), TestProbe().ref, SampleOrders.XSN_LTC_BUY_LIMIT_2)

      val state = OrderManagerState.empty
        .add(order1)
        .add(order2)
        .add(order3)
        .add(peerOrderTwo)
        .remove(XSN_BTC, peerOrder.peer)

      state.find(order1.order.value.id).value must be(order1)
      state.find(order2.order.value.id) must be(empty)
      state.find(order3.order.value.id) must be(empty)
      state.find(peerOrderTwo.order.value.id).value must be(peerOrderTwo)
    }

    "remove pair subscription from a peer" in {
      val peerTwo = TestProbe().ref
      val state = OrderManagerState.empty
        .subscribe(XSN_BTC, peerOrder.peer)
        .subscribe(XSN_LTC, peerOrder.peer)
        .subscribe(XSN_BTC, peerTwo)
        .remove(XSN_BTC, peerOrder.peer)

      state.subscriptors(XSN_BTC) must be(Set(peerTwo))
      state.subscriptors(XSN_LTC) must be(Set(peerOrder.peer))
    }

    "remove all pair orders from a peer from GroupedOrders" in {
      val state = OrderManagerState.empty
        .add(peerOrder)
        .add(PeerOrder(ClientId.random(), peer, SampleOrders.XSN_BTC_BUY_LIMIT_1))
        .add(PeerOrder(ClientId.random(), peer, SampleOrders.XSN_BTC_BUY_LIMIT_2))
        .remove(XSN_BTC, peerOrder.peer)

      val ordersXsnLtc = state.groupedOrders
        .availableFor(XSN_LTC, OrderSide.Sell)
        .values
        .foldLeft(List.empty[XSN_LTC.LimitOrder])((orders, priceOrders) => orders ::: priceOrders.toList)

      val ordersXsnBtc = state.groupedOrders
        .availableFor(XSN_BTC, OrderSide.Sell)
        .values
        .foldLeft(List.empty[XSN_BTC.LimitOrder])((orders, priceOrders) => orders ::: priceOrders.toList)

      val expected = List(peerOrder.order.value)
      ordersXsnLtc mustBe expected

      ordersXsnBtc mustBe empty
    }
  }

  "subscribe" should {
    "subscribe a peer to a trading pair" in {
      val state = OrderManagerState.empty
      val result = state.subscribe(XSN_BTC, peerOrder.peer)
      result.subscriptors(XSN_BTC) must be(Set(peerOrder.peer))

      val result2 = result.subscribe(XSN_LTC, peerOrder.peer)
      result2.subscriptors(XSN_BTC) must be(Set(peerOrder.peer))
      result2.subscriptors(XSN_LTC) must be(Set(peerOrder.peer))
    }

    "subscribe two times to same trading pair must appear once" in {
      val state = OrderManagerState.empty.subscribe(XSN_BTC, peerOrder.peer)
      val result = state.subscribe(XSN_BTC, peerOrder.peer)
      result.subscriptors(XSN_BTC) must be(Set(peerOrder.peer))
    }
  }

  "unsubscribe" should {
    "unsubscribe a peer from BTC currency" in {
      val state = OrderManagerState.empty.subscribe(XSN_BTC, peerOrder.peer)

      val result = state.unsubscribe(XSN_BTC, peerOrder.peer)
      result.subscriptors(XSN_BTC) must be(empty)
    }

    "unsubscribe a peer from no subscribed currency" in {
      val state = OrderManagerState.empty.subscribe(XSN_BTC, peerOrder.peer)
      val result = state.unsubscribe(XSN_LTC, peerOrder.peer)

      result.subscriptors(XSN_LTC) must be(empty)
      result.subscriptors(XSN_BTC) must be(Set(peerOrder.peer))
    }
  }
}
