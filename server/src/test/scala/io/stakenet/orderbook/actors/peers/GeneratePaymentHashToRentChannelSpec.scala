package io.stakenet.orderbook.actors.peers

import java.time.Instant

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GeneratePaymentHashToRentChannel
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  GeneratePaymentHashToRentChannelResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{Currency, Satoshis, TradingPairPrice}
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.Mockito.when
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class GeneratePaymentHashToRentChannelSpec extends PeerSpecBase("GeneratePaymentHashToRentChannelSpec") {
  val connextCurrencies = Currency.values.diff(Currency.forLnd)

  "GeneratePaymentHashToRentChannel" should {
    connextCurrencies.foreach { currency =>
      s"respond with GeneratePaymentHashToRentChannelResponse paying with $currency" in {
        val paymentService = mock[PaymentService]
        val channelsRepository = mock[ChannelsRepository.Blocking]
        val tradesRepository = mock[TradesRepository.Blocking]
        val lnd = mock[MulticurrencyLndClient]

        withSinglePeer(
          paymentService = paymentService,
          channelsRepository = channelsRepository,
          tradesRepository = tradesRepository,
          lnd = lnd
        ) { alice =>
          val requestId = "id"
          val otherCurrency = Currency.values.find(c => Try(TradingPair.from(currency, c)).isSuccess).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = duration)

          val paymentHash = randomPaymentHash()
          when(paymentService.generatePaymentHash(channelFeePayment.payingCurrency)).thenReturn(
            Future.successful(paymentHash)
          )
          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GeneratePaymentHashToRentChannelResponse(channelFeePayment, paymentHash)
          )

          alice.client.expectMsg(expected)
        }
      }

      s"allow to rent a $currency channel paying with $currency" in {
        val paymentService = mock[PaymentService]
        val channelsRepository = mock[ChannelsRepository.Blocking]
        val lnd = mock[MulticurrencyLndClient]

        withSinglePeer(
          paymentService = paymentService,
          channelsRepository = channelsRepository,
          lnd = lnd
        ) { alice =>
          val requestId = "id"
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          val paymentHash = randomPaymentHash()
          when(paymentService.generatePaymentHash(channelFeePayment.payingCurrency)).thenReturn(
            Future.successful(paymentHash)
          )

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GeneratePaymentHashToRentChannelResponse(channelFeePayment, paymentHash)
          )

          alice.client.expectMsg(expected)
        }
      }

      s"fail when duration is more than 7 days for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 7.days.toSeconds + 1
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))

          val error = "Max duration is 7 days"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }

      s"fail when duration is less than 1 hour for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 30.minutes.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))

          val error = "Min duration is 1 hour"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }

      s"fail when max capacity is more than 10k USD for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 7.days.toSeconds
          val capacity = Satoshis.from(BigDecimal(10001)).value
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(
              payingCurrency = currency,
              currency = currency,
              lifeTimeSeconds = duration,
              capacity = capacity
            )

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))

          val maxCapacity = Satoshis.from(BigDecimal("10000.000000000000000000")).value
          val error = s"Max capacity is ${maxCapacity.toString(currency)}(10000 USD)"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }

      s"fail when capacity is less than 5 USD for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 7.days.toSeconds
          val capacity = Helpers.asSatoshis("0.0005")
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(
              payingCurrency = currency,
              currency = currency,
              lifeTimeSeconds = duration,
              capacity = capacity
            )

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))

          val minCapacity = Satoshis.from(BigDecimal("5.000000000000000000")).value
          val error = s"Min capacity is ${minCapacity.toString(currency)}(5 USD)"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }
    }

    Currency.values.diff(connextCurrencies).foreach { currency =>
      s"return an error paying with $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(
              payingCurrency = currency,
              currency = Currency.WETH
            )

          alice.actor ! WebSocketIncomingMessage(requestId, GeneratePaymentHashToRentChannel(channelFeePayment))
          alice.client
            .expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"$currency not supported")))
        }
      }
    }
  }
}
