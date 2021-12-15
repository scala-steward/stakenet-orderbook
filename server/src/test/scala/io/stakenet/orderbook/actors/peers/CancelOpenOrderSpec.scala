package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.peers.protocol.Command.{CancelOpenOrder, GetOpenOrderById, PlaceOrder}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CancelOpenOrderResponse,
  GetOpenOrderByIdResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.trading.TradingPair

class CancelOpenOrderSpec extends PeerSpecBase("CancelOrderSpec") {
  "CancelOrder" should {
    TradingPair.values.foreach { pair =>
      s"cancel a $pair order" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val order = buyLimitOrder(pair)

          alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order, None))
          discardMsg(alice) // order placed

          alice.actor ! WebSocketIncomingMessage(requestId, CancelOpenOrder(order.id))
          alice.client.expectMsg(WebSocketOutgoingMessage(2, Some(requestId), CancelOpenOrderResponse(Some(order))))

          //  Verify that the order can't be retrieved
          alice.actor ! WebSocketIncomingMessage("id", GetOpenOrderById(order.id))
          alice.client.expectMsg(WebSocketOutgoingMessage(3, Some("id"), GetOpenOrderByIdResponse(None)))
        }
      }
    }

    "return None when the order doesn't exist" in {
      withSinglePeer() { alice =>
        val requestId = "id"

        alice.actor ! WebSocketIncomingMessage(requestId, CancelOpenOrder(OrderId.random()))
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CancelOpenOrderResponse(None)))
      }
    }

    "return None when trying to cancel a matched order" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = buyLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))
        val bobOrder = sellLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))

        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, None))
        discardMsg(alice) // order placed

        bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, None))
        discardMsg(bob) // order matched
        discardMsg(alice) // order matched

        alice.actor ! WebSocketIncomingMessage("id", CancelOpenOrder(OrderId.random()))
        alice.client.expectMsg(WebSocketOutgoingMessage(3, Some("id"), CancelOpenOrderResponse(None)))
      }
    }
  }
}
