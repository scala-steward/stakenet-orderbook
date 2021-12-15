package io.stakenet.orderbook.actors.peers

import java.util.concurrent.TimeUnit

import io.stakenet.lssd.protos.swap_packets.{
  InvoiceExchangePacketBody,
  Packet,
  SanitySwapInitPacketBody,
  SwapCompletePacketBody,
  SwapFailedPacketBody
}
import io.stakenet.orderbook.actors.{PeerSpecBase, peers}
import io.stakenet.orderbook.helpers.CustomMatchers
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.TradingPair.XSN_LTC
import io.stakenet.orderbook.models.trading.{Trade, TradingOrder}
import io.stakenet.orderbook.models.{Currency, OrderId, OrderMessage, trading}
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.services.MakerPaymentService
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class SendOrderMessageSpec extends PeerSpecBase("SendOrderMessageSpec") {

  val XSN_LTC_MARKET_ORDER: TradingOrder =
    XSN_LTC.MarketOrder(OrderId.random(), trading.OrderSide.Sell, funds = getSatoshis(10))

  "SendOrderMessage" should {
    "allows both peers to communicate with messages" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob)
        discardMsg(alice)

        val messageProto = SanitySwapInitPacketBody(currency = "XSN", rHash = "45654654654")
        val message = Packet().withInit(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(message, aliceOrder.value.id))
        )
        val expectedAlice = peers.ws.WebSocketOutgoingMessage(
          3,
          Some("id"),
          peers.protocol.Event.CommandResponse.SendOrderMessageResponse()
        )
        alice.client.expectMsg(expectedAlice)

        val expectedBob = peers.ws.WebSocketOutgoingMessage(
          2,
          None,
          peers.protocol.Event.ServerEvent.NewOrderMessage(bobOrder.value.id, message)
        )
        bob.client.expectMsg(expectedBob)

        // bob sends a message to alice
        val messageProto2 = InvoiceExchangePacketBody(rHash = "45654654654", takerPaymentRequest = "1325468723")
        val message2 = Packet().withInvoiceExchange(messageProto2).toByteArray.toVector
        bob.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(message2, bobOrder.value.id))
        )
        val expectedBob2 = peers.ws.WebSocketOutgoingMessage(
          3,
          Some("id"),
          peers.protocol.Event.CommandResponse.SendOrderMessageResponse()
        )
        bob.client.expectMsg(expectedBob2)

        val expectedAlice2 = peers.ws.WebSocketOutgoingMessage(
          4,
          None,
          peers.protocol.Event.ServerEvent.NewOrderMessage(aliceOrder.value.id, message2)
        )
        alice.client.expectMsg(expectedAlice2)
      }
    }

    "Send a Swap Complete message" in {
      val feesRepositoryMock = mock[FeesRepository.Blocking]
      val makerPaymentService = mock[MakerPaymentService]

      when(feesRepositoryMock.find(any[OrderId], any[Currency])).thenReturn(None)
      when(makerPaymentService.payMaker(any[ClientId], any[PeerTrade])).thenReturn(Future.successful(Right(())))

      withTwoPeers(feesRepository = feesRepositoryMock, makerPaymentService = makerPaymentService) { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob)
        discardMsg(alice)

        val messageProto = SwapCompletePacketBody(rHash = "45654654654")

        val swapPacket = Packet().withComplete(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )
        val expectedAlice = peers.ws.WebSocketOutgoingMessage(
          3,
          Some("id"),
          peers.protocol.Event.CommandResponse.SendOrderMessageResponse()
        )
        alice.client.expectMsg(expectedAlice)

        val expectedBob = peers.ws.WebSocketOutgoingMessage(
          2,
          None,
          peers.protocol.Event.ServerEvent.NewOrderMessage(bobOrder.value.id, swapPacket)
        )
        bob.client.expectMsg(expectedBob)
      }
    }

    "Send a Swap Failure message" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob)
        discardMsg(alice)

        val messageProto =
          SwapFailedPacketBody(rHash = "45654654654", failureReason = 1, errorMessage = "error message")

        val swapPacket = Packet().withFail(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )
        val expectedAlice = peers.ws.WebSocketOutgoingMessage(
          3,
          Some("id"),
          peers.protocol.Event.CommandResponse.SendOrderMessageResponse()
        )
        alice.client.expectMsg(expectedAlice)

        val expectedBob = peers.ws.WebSocketOutgoingMessage(
          2,
          None,
          peers.protocol.Event.ServerEvent.NewOrderMessage(bobOrder.value.id, swapPacket)
        )
        bob.client.expectMsg(expectedBob)
      }
    }

    "Send a Swap completed will notify subscribers" in {
      val feesRepositoryMock = mock[FeesRepository.Blocking]
      val makerPaymentService = mock[MakerPaymentService]

      when(feesRepositoryMock.find(any[OrderId], any[Currency])).thenReturn(None)
      when(makerPaymentService.payMaker(any[ClientId], any[PeerTrade])).thenReturn(Future.successful(Right(())))

      withTwoPeers(feesRepository = feesRepositoryMock, makerPaymentService = makerPaymentService) { (alice, bob) =>
        val pair = XSN_LTC
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        discardMsg(bob)
        discardMsg(alice)

        // bob Subscribes to XSN_LTC
        bob.actor ! peers.ws
          .WebSocketIncomingMessage(
            "id",
            peers.protocol.Command.Subscribe(bobOrder.pair, retrieveOrdersSummary = false)
          )
        discardMsg(bob)

        val messageProto =
          SwapCompletePacketBody(rHash = "45654654654")

        val swapPacket = Packet().withComplete(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )

        discardMsg(alice)

        val expectedTrade =
          Trade.from(pair)(pair.use(bobOrder.value).value, pair.useLimitOrder(aliceOrder.value).value)

        // bob gets a notification about the SwapSucces
        bob.client.fishForMessage(Duration(15.0, TimeUnit.SECONDS)) {
          case peers.ws.WebSocketOutgoingMessage(_, None, peers.protocol.Event.ServerEvent.SwapSuccess(trade)) =>
            CustomMatchers.matchTrades(expected = expectedTrade, actual = trade)

            true
          case _ =>
            false
        }
      }
    }

    "Send a Swap Failure will notify subscribers" in {
      withTwoPeers() { (alice, bob) =>
        val pair = XSN_LTC
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        discardMsg(bob)
        discardMsg(alice)

        // bob Subscribes to XSN_LTC
        bob.actor ! peers.ws
          .WebSocketIncomingMessage(
            "id",
            peers.protocol.Command.Subscribe(bobOrder.pair, retrieveOrdersSummary = false)
          )
        discardMsg(bob)

        val messageProto =
          SwapFailedPacketBody(rHash = "45654654654")

        val swapPacket = Packet().withFail(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )

        discardMsg(alice)

        val expectedTrade =
          Trade.from(pair)(pair.use(bobOrder.value).value, pair.useLimitOrder(aliceOrder.value).value)

        // bob gets a notification about the SwapFailure
        bob.client.fishForMessage(Duration(15.0, TimeUnit.SECONDS)) {
          case peers.ws.WebSocketOutgoingMessage(_, None, peers.protocol.Event.ServerEvent.SwapFailure(trade)) =>
            CustomMatchers.matchTrades(expected = expectedTrade, actual = trade)
            true
          case _ =>
            false
        }
      }
    }

    "Fail when try to send a message when the swap was completed" in {
      val feesRepositoryMock = mock[FeesRepository.Blocking]
      val makerPaymentService = mock[MakerPaymentService]

      when(feesRepositoryMock.find(any[OrderId], any[Currency])).thenReturn(None)
      when(makerPaymentService.payMaker(any[ClientId], any[PeerTrade])).thenReturn(Future.successful(Right(())))

      withTwoPeers(feesRepository = feesRepositoryMock, makerPaymentService = makerPaymentService) { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        discardMsg(bob)
        discardMsg(alice)

        val messageProto =
          SwapCompletePacketBody(rHash = "45654654654")

        val swapPacket = Packet().withComplete(messageProto).toByteArray.toVector

        // alice sends the Swap Complete message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )

        discardMsg(alice)
        discardMsg(bob)

        // alice try to send the Swap Complete message to bob again
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )

        val expectedAlice = peers.ws.WebSocketOutgoingMessage(
          4,
          Some("id"),
          peers.protocol.Event.CommandResponse.CommandFailed("The given order doesn't exists or is not matched")
        )
        alice.client.expectMsg(expectedAlice)

        bob.client.expectNoMessage()
      }
    }

    "Send a message to an unknown matched order returns MatchedOrderNotFound" in {
      withSinglePeer() { alice =>
        val order = XSN_LTC_MARKET_ORDER
        val message = "Hello".getBytes().toVector
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(message, order.value.id))
        )

        val expected =
          peers.ws.WebSocketOutgoingMessage(
            1,
            Some("id"),
            peers.protocol.Event.CommandResponse.CommandFailed("The given order doesn't exists or is not matched")
          )
        alice.client.expectMsg(expected)
      }
    }

    "Fail when the message received is an unknown message" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        discardMsg(bob)
        discardMsg(alice)

        val message = "Invalid message".getBytes.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(message, aliceOrder.value.id))
        )

        val expected =
          peers.ws.WebSocketOutgoingMessage(
            3,
            Some("id"),
            peers.protocol.Event.CommandResponse.CommandFailed("The order message is invalid")
          )
        alice.client.expectMsg(expected)

        bob.client.expectNoMessage()
      }
    }
  }
}
