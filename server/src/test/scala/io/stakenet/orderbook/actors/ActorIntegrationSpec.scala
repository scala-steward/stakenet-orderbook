package io.stakenet.orderbook.actors

import akka.actor.PoisonPill
import akka.testkit.{TestKit, TestProbe}
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers.protocol.Event
import io.stakenet.orderbook.actors.peers.protocol.Event.ServerEvent
import io.stakenet.orderbook.actors.peers.results.PlaceOrderResult
import io.stakenet.orderbook.actors.peers.ws.WebSocketOutgoingMessage
import io.stakenet.orderbook.helpers.{CustomMatchers, SampleOrders}
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading._
import org.scalatest.OptionValues._

class ActorIntegrationSpec extends PeerSpecBase("ActorIntegrationSpec") {

  import SampleOrders._
  import TradingPair._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  val XSN_LTC_MARKET_ORDER: TradingOrder =
    XSN_LTC.MarketOrder(OrderId.random(), trading.OrderSide.Sell, funds = getSatoshis(10))
  val feePercent = BigDecimal(0.0025)

  "The actors" must {
    "the manager must store all the orders" in {
      withPeers()() { case TestData(_, orderManager, _) =>
        val probe = TestProbe()
        val orders = List(XSN_BTC_BUY_LIMIT_1, XSN_BTC_BUY_LIMIT_2, XSN_LTC_BUY_LIMIT_1)
        orders.foreach { order =>
          orderManager.ref ! OrderManagerActor.Command.PlaceOrder(order, ClientId.random(), probe.ref)
        }
        orderManager.ref.tell(OrderManagerActor.Command.GetAllOrders, probe.ref)
        probe.expectMsg(OrderManagerActor.Event.OrdersRetrieved(orders))
      }
    }

    "disconnecting alice removes its orders" in {
      withPeers()("alice", "bob") { case TestData(alice :: bob :: Nil, orderManager, _) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        val bobOrder = XSN_BTC_BUY_LIMIT_2

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice) // order placed

        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash)))
        discardMsg(bob) // order placed

        // killing alice removes its orders
        alice.actor ! PoisonPill
        val probe = TestProbe()
        val orders = List(bobOrder)
        orderManager.ref.tell(OrderManagerActor.Command.GetAllOrders, probe.ref)
        probe.expectMsg(OrderManagerActor.Event.OrdersRetrieved(orders))
      }
    }

    "disconnecting alice notifies canceled orders to bob" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        val aliceOrder2 = XSN_BTC_BUY_LIMIT_2

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice) // order placed

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder2, Some(xsnRHash)))
        discardMsg(alice) // order placed

        bob.actor ! peers.ws
          .WebSocketIncomingMessage(
            "id",
            peers.protocol.Command.Subscribe(TradingPair.XSN_LTC, retrieveOrdersSummary = false)
          )
        discardMsg(bob) // subscribed
        bob.actor ! peers.ws
          .WebSocketIncomingMessage(
            "id",
            peers.protocol.Command.Subscribe(TradingPair.XSN_BTC, retrieveOrdersSummary = false)
          )
        discardMsg(bob) // subscribed

        alice.actor ! PoisonPill
        val a = nextMsg(bob)
        val b = nextMsg(bob)
        val canceled = List(a, b).collect {
          case peers.ws.WebSocketOutgoingMessage(_, _, peers.protocol.Event.ServerEvent.OrderCanceled(order)) => order
        }
        canceled.toSet must be(Set(aliceOrder2, aliceOrder))
      }
    }

    "disconnecting alice having matched orders, will notify bob" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        val bobOrder = XSN_LTC_SELL_LIMIT_1

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice) // order placed

        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob) // order matched
        val trade = alice.client.expectMsgPF() {
          case peers.ws.WebSocketOutgoingMessage(_, _, e: peers.protocol.Event.ServerEvent.MyOrderMatched) => e.trade
        }

        alice.actor ! PoisonPill

        val expectedBobMsg =
          peers.ws.WebSocketOutgoingMessage(2, None, peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled(trade))
        bob.client.expectMsg(expectedBobMsg)
      }
    }

    "fail when alice get disconnected and bob tries to send an order message" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        val bobOrder = XSN_LTC_SELL_LIMIT_1

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice) // order placed

        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob) // order matched
        val trade = alice.client.expectMsgPF() {
          case peers.ws.WebSocketOutgoingMessage(_, _, e: peers.protocol.Event.ServerEvent.MyOrderMatched) => e.trade
        }

        alice.actor ! PoisonPill

        val expectedBobMsg =
          peers.ws.WebSocketOutgoingMessage(2, None, peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled(trade))
        bob.client.expectMsg(expectedBobMsg)

        // if bob try to send a message, a command failed will be returned
        val message = "Hello".getBytes().toVector
        bob.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(message, bobOrder.value.id))
        )

        val expectedBobMsg2 =
          peers.ws.WebSocketOutgoingMessage(
            3,
            Some("id"),
            peers.protocol.Event.CommandResponse.CommandFailed("The given order doesn't exists or is not matched")
          )
        bob.client.expectMsg(expectedBobMsg2)
      }
    }

    "tests the complete flow" in {
      withTwoPeers() { (alice, bob) =>
        testCompleteFlow(alice, bob)
      }
    }

    "PeerMessageFilterActor notifies subscribed peers when maintenance starts" in {
      withPeers()("alice", "bob") { case TestData(alice :: bob :: Nil, _, maintenanceManager) =>
        maintenanceManager.ref ! PeerMessageFilterActor.Command.PeerDisconnected(alice.actor)
        maintenanceManager.ref ! PeerMessageFilterActor.Command.StartMaintenance()

        alice.client.expectNoMessage()
        bob.client.expectMsg(WebSocketOutgoingMessage(1, None, ServerEvent.MaintenanceInProgress()))
      }
    }

    "PeerMessageFilterActor notifies subscribed peers when maintenance is completed" in {
      withPeers()("alice", "bob") { case TestData(alice :: bob :: Nil, _, maintenanceManager) =>
        maintenanceManager.ref ! PeerMessageFilterActor.Command.PeerDisconnected(alice.actor)
        maintenanceManager.ref ! PeerMessageFilterActor.Command.StartMaintenance()
        maintenanceManager.ref ! PeerMessageFilterActor.Command.CompleteMaintenance()

        alice.client.expectNoMessage()
        bob.client.expectMsg(WebSocketOutgoingMessage(1, None, ServerEvent.MaintenanceInProgress()))
        bob.client.expectMsg(WebSocketOutgoingMessage(2, None, ServerEvent.MaintenanceCompleted()))
      }
    }
  }

  private def testCompleteFlow[T](alice: Peer, bob: Peer): Unit = {
    val aliceMatchedOrder = XSN_BTC_BUY_LIMIT_1
    val bobMatchedOrder = XSN_BTC_SELL_LIMIT_1
    val pair = XSN_BTC

    // bob subscribes to XSN_BTC
    bob.actor ! peers.ws
      .WebSocketIncomingMessage("id", peers.protocol.Command.Subscribe(pair, retrieveOrdersSummary = true))
    bob.client.expectMsg(
      peers.ws.WebSocketOutgoingMessage(
        1,
        Some("id"),
        peers.protocol.Event.CommandResponse.SubscribeResponse(TradingPair.XSN_BTC, List.empty, List.empty)
      )
    )

    // alice places an order
    alice.actor ! peers.ws
      .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceMatchedOrder, Some(xsnRHash)))
    alice.client.expectMsg(
      peers.ws.WebSocketOutgoingMessage(
        1,
        Some("id"),
        peers.protocol.Event.CommandResponse.PlaceOrderResponse(
          PlaceOrderResult.OrderPlaced(aliceMatchedOrder)
        )
      )
    )

    // then, bob gets a notification
    bob.client.expectMsg(
      peers.ws
        .WebSocketOutgoingMessage(2, None, peers.protocol.Event.ServerEvent.OrderPlaced(aliceMatchedOrder))
    )

    // then, bob places its order which gets matched
    bob.actor ! peers.ws
      .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobMatchedOrder, Some(xsnRHash)))
    val expectedBobTrade =
      Trade.from(pair)(pair.use(bobMatchedOrder.value).value, pair.useLimitOrder(aliceMatchedOrder.value).value)

    // bob receive the server event and the command response from server
    val bobMsg1 = nextMsg(bob)
    val bobMsg2 = nextMsg(bob)
    val receivedTrades = List(bobMsg1, bobMsg2).collect {
      case peers.ws.WebSocketOutgoingMessage(_, None, peers.protocol.Event.ServerEvent.OrdersMatched(trade)) =>
        trade
      case peers.ws.WebSocketOutgoingMessage(
            _,
            _,
            Event.CommandResponse.PlaceOrderResponse(PlaceOrderResult.OrderMatched(trade, order))
          ) =>
        order mustBe aliceMatchedOrder
        trade
    }
    receivedTrades.length must be(2)
    receivedTrades.foreach(trade => CustomMatchers.matchTrades(expected = expectedBobTrade, actual = trade))

    // alice gets notified because its order was matched
    val _ = alice.client.expectMsg(
      peers.ws.WebSocketOutgoingMessage(2, None, Event.ServerEvent.MyOrderMatched(receivedTrades.head, bobMatchedOrder))
    )
  }
}
