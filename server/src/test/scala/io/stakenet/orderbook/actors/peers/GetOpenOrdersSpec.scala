package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.{GetOpenOrders, PlaceOrder}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.GetOpenOrdersResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.trading.{OrderSummary, TradingPair}

class GetOpenOrdersSpec extends PeerSpecBase("GetOpenOrdersSpec") {
  "GetOpenOrders" should {
    TradingPair.values.foreach { pair =>
      s"retrieve the available orders for $pair" in {
        withTwoPeers() { (alice, bob) =>
          val order1 = buyLimitOrder(pair, price = getSatoshis(100), funds = getSatoshis(50))
          val order2 = buyLimitOrder(pair, price = getSatoshis(200), funds = getSatoshis(100))
          val order3 = buyLimitOrder(pair, price = getSatoshis(200), funds = getSatoshis(150))
          val order4 = sellLimitOrder(pair, price = getSatoshis(500), funds = getSatoshis(200))
          val orders = List(order1, order2, order3, order4)

          orders.foreach { order =>
            val requestId = "id"
            alice.actor ! WebSocketIncomingMessage(requestId, PlaceOrder(order, None))
            discardMsg(alice) // order placed
          }

          val requestId = "otherId"
          bob.actor ! WebSocketIncomingMessage(requestId, GetOpenOrders(pair))

          val bids = List(
            OrderSummary(getSatoshis(200), getSatoshis(250)),
            OrderSummary(getSatoshis(100), getSatoshis(50))
          )

          val asks = List(
            OrderSummary(getSatoshis(500), getSatoshis(200))
          )

          val expected = WebSocketOutgoingMessage(1, Some(requestId), GetOpenOrdersResponse(pair, bids, asks))
          bob.client.expectMsg(expected)
        }
      }
    }
  }
}
