package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.{CleanTradingPairOrders, GetOpenOrderById, PlaceOrder}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CleanTradingPairOrdersResponse,
  GetOpenOrderByIdResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.trading.TradingPair

class CleanTradingPairOrdersSpec extends PeerSpecBase("CleanTradingPairOrdersSpec") {
  "CleanTradingPairOrders" should {
    TradingPair.values.foreach { pair =>
      s"clear $pair orders" in {
        withTwoPeers() { (alice, bob) =>
          val order1 = buyLimitOrder(pair, price = getSatoshis(100))
          val order2 = buyLimitOrder(pair, price = getSatoshis(150))
          val order3 = sellLimitOrder(pair, price = getSatoshis(150))

          alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(order1, None))
          discardMsg(alice)

          alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(order2, None))
          discardMsg(alice)

          bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(order3, None))
          discardMsg(bob) //order matched
          discardMsg(alice) //order matched

          alice.actor ! WebSocketIncomingMessage("id", CleanTradingPairOrders(pair))

          val expected = WebSocketOutgoingMessage(
            4,
            Some("id"),
            CleanTradingPairOrdersResponse(pair, List(order1.id), List(order2.id))
          )

          alice.client.expectMsg(expected)

          alice.actor ! WebSocketIncomingMessage("id", GetOpenOrderById(order1.id))
          alice.client.expectMsg(WebSocketOutgoingMessage(5, Some("id"), GetOpenOrderByIdResponse(None)))

          alice.actor ! WebSocketIncomingMessage("id", GetOpenOrderById(order2.id))
          alice.client.expectMsg(WebSocketOutgoingMessage(6, Some("id"), GetOpenOrderByIdResponse(None)))
        }
      }
    }
  }
}
