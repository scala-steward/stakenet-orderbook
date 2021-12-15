package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.{PeerSpecBase, peers}

class PingSpec extends PeerSpecBase("PingSpec") {
  "Ping" should {
    "respond" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val expected =
          peers.ws.WebSocketOutgoingMessage(1, Some(requestId), peers.protocol.Event.CommandResponse.PingResponse())
        alice.actor ! peers.ws.WebSocketIncomingMessage(requestId, peers.protocol.Command.Ping())
        alice.client.expectMsg(expected)
      }
    }
  }
}
