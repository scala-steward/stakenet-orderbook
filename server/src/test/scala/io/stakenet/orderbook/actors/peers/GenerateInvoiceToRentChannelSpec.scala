package io.stakenet.orderbook.actors.peers

import java.time.Instant

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GenerateInvoiceToRentChannel
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  GenerateInvoiceToRentChannelResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.lnd.LndHelper.GetBalanceError
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

class GenerateInvoiceToRentChannelSpec extends PeerSpecBase("GenerateInvoiceToRentChannelSpec") {
  "GenerateInvoiceToRentChannel" should {
    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> Helpers.asSatoshis("0.023511967413958902"),
        Currency.BTC -> Helpers.asSatoshis("0.03012839478756"),
        Currency.LTC -> Helpers.asSatoshis("0.03012839478756")
      )

      s"respond with GenerateInvoiceToRentChannelResponse paying with $currency" in {
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
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          val fee = fees.get(currency).value
          val duration = 5.days.toSeconds
          val hubBalance = Helpers.asSatoshis("9999999.99999999")
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = duration)

          val memo = "Fee for renting channel"
          when(paymentService.createPaymentRequest(channelFeePayment.payingCurrency, fee, memo)).thenReturn(
            Future.successful(Right("test"))
          )

          val paymentHash = randomPaymentHash()
          when(paymentService.getPaymentHash(channelFeePayment.payingCurrency, "test")).thenReturn(
            Future.successful(paymentHash)
          )
          when(channelsRepository.createChannelFeePayment(channelFeePayment, paymentHash, fee)).thenReturn(())
          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))
          when(lnd.getBalance(channelFeePayment.currency)).thenReturn(Future.successful(hubBalance))

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GenerateInvoiceToRentChannelResponse(channelFeePayment, "test")
          )

          alice.client.expectMsg(expected)
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> Helpers.asSatoshis("0.024404"),
        Currency.BTC -> Helpers.asSatoshis("0.02902712"),
        Currency.LTC -> Helpers.asSatoshis("0.024404")
      )

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
          val fee = fees.get(currency).value
          val duration = 5.days.toSeconds
          val hubBalance = Helpers.asSatoshis("9999999.99999999")
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          val memo = "Fee for renting channel"
          when(paymentService.createPaymentRequest(channelFeePayment.payingCurrency, fee, memo)).thenReturn(
            Future.successful(Right("test"))
          )

          val paymentHash = randomPaymentHash()
          when(paymentService.getPaymentHash(channelFeePayment.payingCurrency, "test")).thenReturn(
            Future.successful(paymentHash)
          )
          when(channelsRepository.createChannelFeePayment(channelFeePayment, paymentHash, fee)).thenReturn(())
          when(lnd.getBalance(channelFeePayment.currency)).thenReturn(Future.successful(hubBalance))

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GenerateInvoiceToRentChannelResponse(channelFeePayment, "test")
          )

          alice.client.expectMsg(expected)
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      s"fail when duration is more than 7 days for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 7.days.toSeconds + 1
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

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

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

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

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

          val error = s"Max capacity is 10000.00000000 $currency(10000 USD)"
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

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

          val error = s"Min capacity is 5.00000000 $currency(5 USD)"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }

      s"fail when hub does not have enough balance $currency" in {
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
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          val duration = 5.days.toSeconds
          val hubBalance = Satoshis.One
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(currency = currency, payingCurrency = otherCurrency, lifeTimeSeconds = duration)

          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))
          when(lnd.getBalance(channelFeePayment.currency)).thenReturn(Future.successful(hubBalance))

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

          val error = "Not enough available funds on this HUB. Please try a smaller amount"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }

      s"fail when hub's balance could not be obtained $currency" in {
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
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(currency = currency, payingCurrency = otherCurrency, lifeTimeSeconds = duration)

          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))
          when(lnd.getBalance(channelFeePayment.currency)).thenReturn(
            Future.failed(new GetBalanceError.InvalidBalance(-1))
          )

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))

          val error = "Got an invalid balance from lnd: -1"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      s"return an error paying with $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = currency)

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))
          alice.client
            .expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"$currency not supported")))
        }
      }
    }
  }
}
