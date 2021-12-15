package io.stakenet.orderbook.actors.peers

import java.time.Instant
import java.util.UUID

import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GetFeeToExtendRentedChannel
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  GetFeeToExtendRentedChannelResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{ChannelId, Currency, TradingPairPrice}
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.trades.TradesRepository
import org.mockito.Mockito.when
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._

import scala.concurrent.duration._
import scala.util.Try

class GetFeeToExtendRentedChannelSpec extends PeerSpecBase("GetFeeToExtendRentedChannelSpec") {
  "GetFeeToExtendRentedChannel" should {
    Currency.values.foreach { currency =>
      val fees = Map(
        Currency.XSN -> Helpers.asSatoshis("0.019440000176904001"),
        Currency.BTC -> Helpers.asSatoshis("0.02962962936"),
        Currency.LTC -> Helpers.asSatoshis("0.02962962936"),
        Currency.ETH -> Helpers.asSatoshis("0.02962962936"),
        Currency.WETH -> Helpers.asSatoshis("0.02962962936"),
        Currency.USDT -> Helpers.asSatoshis("0.02962962936"),
        Currency.USDC -> Helpers.asSatoshis("0.02962962936")
      )

      s"respond with GetFeeToRentChannelResponse for $currency" in {
        val channelsRepository = mock[ChannelsRepository.Blocking]
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(channelsRepository = channelsRepository, tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val otherCurrency = Currency.values.filter(c => Try(TradingPair.from(c, currency)).isSuccess).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(currency = otherCurrency, payingCurrency = currency, lifeTimeSeconds = duration)
          val channelId = ChannelId.LndChannelId.random()
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)

          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GetFeeToExtendRentedChannel(channelId.value, currency, duration)
          )

          val expectedAmount = fees.get(currency).value
          alice.client.expectMsg(
            WebSocketOutgoingMessage(1, Some(requestId), GetFeeToExtendRentedChannelResponse(expectedAmount))
          )
        }
      }
    }

    Currency.values.foreach { currency =>
      val fees = Map(
        Currency.XSN -> Helpers.asSatoshis("0.024"),
        Currency.BTC -> Helpers.asSatoshis("0.024"),
        Currency.LTC -> Helpers.asSatoshis("0.024"),
        Currency.ETH -> Helpers.asSatoshis("0.024"),
        Currency.WETH -> Helpers.asSatoshis("0.024"),
        Currency.USDT -> Helpers.asSatoshis("0.024"),
        Currency.USDC -> Helpers.asSatoshis("0.024")
      )

      s"allow to extend a $currency channel paying with $currency" in {
        val channelsRepository = mock[ChannelsRepository.Blocking]
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(channelsRepository = channelsRepository, tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(currency = currency, payingCurrency = currency, lifeTimeSeconds = duration)
          val channelId = ChannelId.LndChannelId.random()

          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GetFeeToExtendRentedChannel(channelId.value, currency, duration)
          )

          val expectedAmount = fees.get(currency).value
          alice.client.expectMsg(
            WebSocketOutgoingMessage(1, Some(requestId), GetFeeToExtendRentedChannelResponse(expectedAmount))
          )
        }
      }
    }

    Currency.values.foreach { currency =>
      s"fail when duration is more than 7 days for $currency" in {
        val channelsRepository = mock[ChannelsRepository.Blocking]

        withSinglePeer(channelsRepository = channelsRepository) { alice =>
          val requestId = "id"
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val duration = 7.days.toSeconds + 1
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(currency = otherCurrency, payingCurrency = currency, lifeTimeSeconds = duration)
          val channelId = ChannelId.LndChannelId.random()

          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GetFeeToExtendRentedChannel(channelId.value, currency, duration)
          )

          val error = "Max duration is 7 days"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }
    }

    "Get the correct amount when the paying currency for extend is not the same as the paying for rental" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository, tradesRepository = tradesRepository) { alice =>
        val requestId = "id"
        val duration = 5.days.toSeconds
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(lifeTimeSeconds = duration)
        val channelId = SampleChannels.newChannel().channelId
        val seconds = channelFeePayment.lifeTimeSeconds

        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(tradesRepository.getLastPrice(TradingPair.LTC_BTC)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GetFeeToExtendRentedChannel(channelId.value, Currency.LTC, seconds)
        )

        val expectedAmount = Helpers.asSatoshis("4.016971705455549697")
        val expected = WebSocketOutgoingMessage(1, Some(requestId), GetFeeToExtendRentedChannelResponse(expectedAmount))
        alice.client.expectMsg(expected)
      }
    }
  }

  "fail when channel does not exist" in {
    val channelsRepository = mock[ChannelsRepository.Blocking]

    withSinglePeer(channelsRepository = channelsRepository) { alice =>
      val requestId = "id"
      val channelId = UUID.randomUUID()

      when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId))).thenReturn(None)
      when(channelsRepository.findChannelFeePayment(ChannelId.ConnextChannelId(channelId))).thenReturn(None)

      alice.actor ! WebSocketIncomingMessage(requestId, GetFeeToExtendRentedChannel(channelId, Currency.XSN, 100))

      val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"Channel $channelId not found"))
      alice.client.expectMsg(expected)
    }
  }
}
