package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.peers.protocol.Command.{PlaceOrder, Subscribe}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.SubscribeResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.trading.{OrderSummary, TradingPair}

class SubscribeSpec extends PeerSpecBase("SubscribeSpec") {
  "Subscribe" should {
    TradingPair.values.foreach { pair =>
      s"include the orders summary in the response for $pair" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val order1 = sellLimitOrder(pair, price = getSatoshis(100))
          val order2 = buyLimitOrder(pair, price = getSatoshis(50))

          alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order1, Some(xsnRHash)))
          discardMsg(alice) // order placed

          alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order2, Some(xsnRHash2)))
          discardMsg(alice) // order placed

          alice.actor ! WebSocketIncomingMessage(requestId, Subscribe(pair, retrieveOrdersSummary = true))

          val summaryBids = List(
            OrderSummary(getSatoshis(50), order2.value.funds)
          )

          val summaryAsks = List(
            OrderSummary(getSatoshis(100), order1.value.funds)
          )

          val expected = WebSocketOutgoingMessage(3, Some(requestId), SubscribeResponse(pair, summaryBids, summaryAsks))
          alice.client.expectMsg(expected)
        }
      }

      s"not include the orders summary in the response $pair" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val order1 = sellLimitOrder(pair, price = getSatoshis(100))
          val order2 = buyLimitOrder(pair, price = getSatoshis(50))

          alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order1, Some(xsnRHash)))
          discardMsg(alice) // order placed

          alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order2, Some(xsnRHash2)))
          discardMsg(alice) // order placed

          alice.actor ! WebSocketIncomingMessage(requestId, Subscribe(pair, retrieveOrdersSummary = false))

          val expected = WebSocketOutgoingMessage(3, Some(requestId), SubscribeResponse(pair, List.empty, List.empty))
          alice.client.expectMsg(expected)
        }
      }
    }

    "not include orders from other pairs" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order1 = sellLimitOrder(TradingPair.XSN_BTC)
        val order2 = buyLimitOrder(TradingPair.XSN_WETH)

        alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order1, Some(xsnRHash)))
        discardMsg(alice) // order placed

        alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order2, Some(xsnRHash2)))
        discardMsg(alice) // order placed

        val pair = TradingPair.LTC_BTC
        alice.actor ! WebSocketIncomingMessage(requestId, Subscribe(pair, retrieveOrdersSummary = true))

        val expected = WebSocketOutgoingMessage(3, Some(requestId), SubscribeResponse(pair, List.empty, List.empty))
        alice.client.expectMsg(expected)
      }
    }
  }
}
