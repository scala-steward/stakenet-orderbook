package io.stakenet.orderbook.actors.peers

import java.time.{Duration, Instant}

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GenerateInvoiceToExtendRentedChannel
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  GenerateInvoiceToExtendRentedChannelResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.lnd.InvalidPaymentHash
import io.stakenet.orderbook.models.lnd.ChannelStatus
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{ChannelId, Currency, Satoshis, TradingPairPrice}
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.MockitoSugar.{when, _}
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration._

class GenerateInvoiceToExtendRentedChannelSpec extends PeerSpecBase("GenerateInvoiceToRentChannelSpec") {
  "GenerateInvoiceToExtendRentedChannel" should {
    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> Helpers.asSatoshis("0.0043740000398034"),
        Currency.BTC -> Helpers.asSatoshis("0.006666666606"),
        Currency.LTC -> Helpers.asSatoshis("0.006666666606")
      )

      s"respond with GenerateInvoiceToExtendRentedChannelResponse for $currency" in {
        val paymentService = mock[PaymentService]
        val channelsRepository = mock[ChannelsRepository.Blocking]
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(
          paymentService = paymentService,
          channelsRepository = channelsRepository,
          tradesRepository = tradesRepository
        ) { alice =>
          val requestId = "id"
          val expiresAt = Instant.now.plus(Duration.ofMinutes(15))
          val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
          val channelId = channel.channelId
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = 100000L)
          val seconds = channelFeePayment.lifeTimeSeconds
          val invoice = "invoice"
          val tradingPairPrice = TradingPairPrice(
            tradingPair,
            Satoshis.from(BigDecimal("1.23456789")).value,
            Instant.now
          )
          val fee = fees.get(currency).value
          val memo = "Fee to extend rented channel"
          val paymentHash = randomPaymentHash()

          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))
          when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
          when(paymentService.createPaymentRequest(currency, fee, memo)).thenReturn(Future.successful(Right(invoice)))
          when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(paymentHash))
          when(channelsRepository.requestRentedChannelExtension(paymentHash, currency, channelId, fee, seconds))
            .thenReturn(())

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
          )

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GenerateInvoiceToExtendRentedChannelResponse(channelId, currency, seconds, invoice)
          )
          alice.client.expectMsg(expected)
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> Helpers.asSatoshis("0.0054"),
        Currency.BTC -> Helpers.asSatoshis("0.0054"),
        Currency.LTC -> Helpers.asSatoshis("0.0054")
      )

      s"allow to extend a $currency channel paying with $currency" in {
        val paymentService = mock[PaymentService]
        val channelsRepository = mock[ChannelsRepository.Blocking]
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(
          paymentService = paymentService,
          channelsRepository = channelsRepository,
          tradesRepository = tradesRepository
        ) { alice =>
          val requestId = "id"
          val expiresAt = Instant.now.plus(Duration.ofMinutes(15))
          val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
          val channelId = channel.channelId
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = 100000L)
          val seconds = channelFeePayment.lifeTimeSeconds
          val invoice = "invoice"
          val fee = fees.get(currency).value
          val memo = "Fee to extend rented channel"
          val paymentHash = randomPaymentHash()

          when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
          when(paymentService.createPaymentRequest(currency, fee, memo)).thenReturn(Future.successful(Right(invoice)))
          when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(paymentHash))
          when(channelsRepository.requestRentedChannelExtension(paymentHash, currency, channelId, fee, seconds))
            .thenReturn(())

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
          )

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GenerateInvoiceToExtendRentedChannelResponse(channelId, currency, seconds, invoice)
          )
          alice.client.expectMsg(expected)
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      s"fail when duration is more than 7 days for $currency" in {
        val channelsRepository = mock[ChannelsRepository.Blocking]

        withSinglePeer(channelsRepository = channelsRepository) { alice =>
          val requestId = "id"
          val expiresAt = Instant.now.plus(Duration.ofMinutes(15))
          val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
          val channelId = channel.channelId
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val duration = 7.days.toSeconds + 1
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = duration)

          when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GenerateInvoiceToExtendRentedChannel(channelId, currency, duration)
          )

          val error = "Max duration is 7 days"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      s"return an error for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val channelId = ChannelId.LndChannelId.random()
          val seconds = 100000L

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
          )
          alice.client
            .expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"$currency not supported")))
        }
      }
    }

    "Get the correct amount when the paying currency for extend is not the same as the paying for rental" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        tradesRepository = tradesRepository
      ) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(20))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val payingCurrency = Currency.LTC
        val seconds = 100000L
        val invoice = "invoice"
        val fee = Satoshis.from(BigDecimal("0.903818633727498681")).value
        val memo = "Fee to extend rented channel"
        val paymentHash = randomPaymentHash()

        when(tradesRepository.getLastPrice(TradingPair.LTC_BTC)).thenReturn(None)
        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.createPaymentRequest(payingCurrency, fee, memo))
          .thenReturn(Future.successful(Right(invoice)))
        when(paymentService.getPaymentHash(payingCurrency, invoice)).thenReturn(Future.successful(paymentHash))
        when(channelsRepository.requestRentedChannelExtension(paymentHash, payingCurrency, channelId, fee, seconds))
          .thenReturn(())

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, payingCurrency, seconds)
        )

        val expected = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          GenerateInvoiceToExtendRentedChannelResponse(channelId, payingCurrency, seconds, invoice)
        )
        alice.client.expectMsg(expected)
      }
    }

    "fail when channel fee payment does not exist" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(ChannelId.ConnextChannelId(channelId.value))).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"Channel $channelId not found"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when an error occurs while fetching the channel" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(paymentService = paymentService, channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenThrow(new RuntimeException("Timeout"))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Timeout"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when payment request could not be created" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        tradesRepository = tradesRepository
      ) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100000L
        val fee = Satoshis.from(BigDecimal("830.769230769230769230")).value
        val memo = "Fee to extend rented channel"

        when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)
        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.createPaymentRequest(currency, fee, memo)).thenReturn(
          Future.failed(new RuntimeException("Connection error"))
        )

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Connection error"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when payment hash is invalid" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        tradesRepository = tradesRepository
      ) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100000L
        val invoice = "invoice"
        val fee = Satoshis.from(BigDecimal("830.769230769230769230")).value
        val memo = "Fee to extend rented channel"

        when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)
        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.createPaymentRequest(currency, fee, memo)).thenReturn(Future.successful(Right(invoice)))
        when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.failed(InvalidPaymentHash("asd")))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val error = "LND Returned something that's not a payment hash: asd"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "fail when payment hash could not be obtained for an unknown error" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        tradesRepository = tradesRepository
      ) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100000L
        val invoice = "invoice"
        val fee = Satoshis.from(BigDecimal("830.769230769230769230")).value
        val memo = "Fee to extend rented channel"

        when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)
        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.createPaymentRequest(currency, fee, memo)).thenReturn(Future.successful(Right(invoice)))
        when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.failed(new RuntimeException("Error")))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Error"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when extension request could not be stored in the database" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        tradesRepository = tradesRepository
      ) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100000L
        val invoice = "invoice"
        val fee = Satoshis.from(BigDecimal("830.769230769230769230")).value
        val memo = "Fee to extend rented channel"
        val paymentHash = randomPaymentHash()

        when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)
        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.createPaymentRequest(currency, fee, memo)).thenReturn(Future.successful(Right(invoice)))
        when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(paymentHash))
        when(channelsRepository.requestRentedChannelExtension(paymentHash, currency, channelId, fee, seconds))
          .thenThrow(new RuntimeException("Connection refused"))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Connection refused"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when channel does not exist" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.minus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findChannel(channelId)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val error = s"Channel $channelId not found"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "fail when channel is not active" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(paymentService = paymentService, channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.minus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val error = s"channel $channelId is not active"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "fail when channel is active but has no expiration date" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(paymentService = paymentService, channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active)
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val error = "Invalid state, active channel without expiration date"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "fail when channel expires in less than 10 minutes" in {
      val paymentService = mock[PaymentService]
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(paymentService = paymentService, channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.minus(Duration.ofMinutes(5))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findChannel(channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GenerateInvoiceToExtendRentedChannel(channelId, currency, seconds)
        )

        val error = s"Channel $channelId expires in less than 10 minutes"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }
  }
}
