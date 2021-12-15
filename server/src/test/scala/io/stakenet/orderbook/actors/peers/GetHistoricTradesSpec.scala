package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.peers.protocol.Command.GetHistoricTrades
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.GetHistoricTradesResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.trading.{Trade, TradingPair}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import org.mockito.MockitoSugar._

class GetHistoricTradesSpec extends PeerSpecBase("GetHistoricTradesSpec") {
  "GetHistoricTrades" should {
    TradingPair.values.foreach { pair =>
      s"respond with empty $pair orders" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"

          when(tradesRepository.getTrades(10, None, pair)).thenReturn(List.empty)
          alice.actor ! WebSocketIncomingMessage(requestId, GetHistoricTrades(10, None, pair))
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), GetHistoricTradesResponse(List.empty)))
        }
      }

      s"respond with some $pair orders" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val trades = List(
            Trade.from(pair)(buyLimitOrder(pair), sellLimitOrder(pair)),
            Trade.from(pair)(buyLimitOrder(pair), sellLimitOrder(pair)),
            Trade.from(pair)(buyLimitOrder(pair), sellLimitOrder(pair))
          )

          when(tradesRepository.getTrades(10, None, pair)).thenReturn(trades)

          alice.actor ! WebSocketIncomingMessage(requestId, GetHistoricTrades(10, None, pair))
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), GetHistoricTradesResponse(trades)))
        }
      }
    }
  }
}
