package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.peers.protocol.Command.{GetOpenOrderById, PlaceOrder}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.GetOpenOrderByIdResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.trading.TradingPair

class GetOpenOrderByIdSpec extends PeerSpecBase("GetOpenOrderByIdSpec") {
  "GetOpenOrderById" should {
    TradingPair.values.foreach { pair =>
      s"return a $pair order" in {
        withTwoPeers() { (alice, bob) =>
          val requestId = "id"
          val order = buyLimitOrder(pair)

          alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order, None))
          discardMsg(alice)

          bob.actor ! WebSocketIncomingMessage(requestId, GetOpenOrderById(order.value.id))

          val expected = WebSocketOutgoingMessage(1, Some(requestId), GetOpenOrderByIdResponse(Some(order)))
          bob.client.expectMsg(expected)
        }
      }
    }

    s"return None when the order does not exist" in {
      withSinglePeer() { bob =>
        val requestId = "id"
        val order = buyLimitOrder(TradingPair.XSN_BTC)

        bob.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order, None))
        discardMsg(bob)

        bob.actor ! WebSocketIncomingMessage(requestId, GetOpenOrderById(OrderId.random()))

        val expected = WebSocketOutgoingMessage(2, Some(requestId), GetOpenOrderByIdResponse(None))
        bob.client.expectMsg(expected)
      }
    }
  }
}
