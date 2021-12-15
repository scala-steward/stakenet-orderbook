package io.stakenet.orderbook.actors.peers

import java.time.Instant

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GetBarsPrices
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.GetBarsPricesResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.models.trading.{Bars, Resolution, TradingPair}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.helpers.SampleOrders._
import org.mockito.MockitoSugar.{mock, when}

class GetBarsPricesSpec extends PeerSpecBase("GetBarsPricesSpec") {
  "GetBarsPrices" should {
    TradingPair.values.foreach { pair =>
      s"respond with no prices for $pair" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val now = Instant.now

          when(tradesRepository.getBars(pair, Resolution(months = 2), now, now, 10)).thenReturn(List.empty)
          alice.actor ! WebSocketIncomingMessage(requestId, GetBarsPrices(pair, Resolution(months = 2), now, now, 10))
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), GetBarsPricesResponse(List.empty)))
        }
      }

      s"respond with some prices for $pair" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val now = Instant.now
          val prices = List(
            Bars(now, getSatoshis(1), getSatoshis(2), getSatoshis(3), getSatoshis(4), 5L),
            Bars(now, getSatoshis(5), getSatoshis(6), getSatoshis(7), getSatoshis(8), 5L),
            Bars(now, getSatoshis(6), getSatoshis(7), getSatoshis(8), getSatoshis(9), 5L)
          )

          when(tradesRepository.getBars(pair, Resolution(months = 2), now, now, 10)).thenReturn(prices)
          alice.actor ! WebSocketIncomingMessage(requestId, GetBarsPrices(pair, Resolution(months = 2), now, now, 10))
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), GetBarsPricesResponse(prices)))
        }
      }
    }
  }
}
