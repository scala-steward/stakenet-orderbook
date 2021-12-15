package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.peers.protocol.Command.Unsubscribe
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.UnsubscribeResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.models.trading.TradingPair

class UnsubscribeSpec extends PeerSpecBase("UnsubscribeSpec") {
  "Unsubscribe" should {
    TradingPair.values.foreach { pair =>
      s"respond to Unsubscribe for $pair" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          alice.actor ! WebSocketIncomingMessage(requestId, Unsubscribe(pair))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), UnsubscribeResponse(pair)))
        }
      }
    }

    "stop notifications" in {
      // TODO: implement this shit
      pending
    }
  }
}
