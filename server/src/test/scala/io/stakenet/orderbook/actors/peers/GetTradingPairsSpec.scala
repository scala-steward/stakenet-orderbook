package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.{PeerSpecBase, peers}
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.trading.TradingPair.XSN_LTC

class GetTradingPairsSpec extends PeerSpecBase("GetTradingPairsSpec") {

  private def testWith(pairs: Set[TradingPair]) = {
    val config = TradingPairsConfig(pairs)
    withSinglePeer(tradingPairsConfig = config) { alice =>
      val requestId = "id"
      val expected = peers.ws.WebSocketOutgoingMessage(
        1,
        Some(requestId),
        peers.protocol.Event.CommandResponse.GetTradingPairsResponse(pairs.toList, false)
      )
      alice.actor ! peers.ws.WebSocketIncomingMessage(requestId, peers.protocol.Command.GetTradingPairs())
      alice.client.expectMsg(expected)
    }
  }

  "GetTradingPairs" should {
    "respond to GetTradingPairs" in {
      testWith(TradingPair.values.toSet)
    }

    "respond only with the enabled pairs" in {
      val input = Set(TradingPair.XSN_BTC, XSN_LTC)
      testWith(input)
    }
  }
}
