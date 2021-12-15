package io.stakenet.orderbook.actors.peers

import java.time.{Duration, Instant}

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GeneratePaymentHashToExtendConnextRentedChannel
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  GeneratePaymentHashToExtendConnextRentedChannelResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.MockitoSugar.{when, _}
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class GeneratePaymentHashToExtendConnextRentedChannelSpec
    extends PeerSpecBase("GeneratePaymentHashToExtendRentedChannelSpec") {
  "GeneratePaymentHashToExtendRentedChannel" should {
    Currency.values.diff(Currency.forLnd).foreach { currency =>
      val fees = Map(
        Currency.ETH -> Helpers.asSatoshis("0.006666666606"),
        Currency.WETH -> Helpers.asSatoshis("0.006666666606"),
        Currency.USDC -> Helpers.asSatoshis("0.006666666606"),
        Currency.USDT -> Helpers.asSatoshis("0.006666666606")
      )

      s"respond with GeneratePaymentHashToExtendRentedChannelResponse for $currency" in {
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
          val channel = SampleChannels
            .newConnextChannel()
            .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
          val channelId = channel.channelId
          val otherCurrency = Currency.values.filter(c => Try(TradingPair.from(c, currency)).isSuccess).head
          val tradingPair = TradingPair.from(currency, otherCurrency)
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = 100000L)
          val seconds = channelFeePayment.lifeTimeSeconds
          val paymentHash = Helpers.randomPaymentHash()
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          val fee = fees.get(currency).value

          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))
          when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
          when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
          when(paymentService.generatePaymentHash(currency)).thenReturn(Future.successful(paymentHash))
          when(channelsRepository.requestRentedChannelExtension(paymentHash, currency, channelId, fee, seconds))
            .thenReturn(())

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
          )

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GeneratePaymentHashToExtendConnextRentedChannelResponse(channelId, currency, seconds, paymentHash)
          )
          alice.client.expectMsg(expected)
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      val fees = Map(
        Currency.ETH -> Helpers.asSatoshis("0.006666666606"),
        Currency.WETH -> Helpers.asSatoshis("0.006666666606"),
        Currency.USDC -> Helpers.asSatoshis("0.006666666606"),
        Currency.USDT -> Helpers.asSatoshis("0.006666666606")
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
          val channel = SampleChannels
            .newConnextChannel()
            .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
          val channelId = channel.channelId
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = 100000L)
          val seconds = channelFeePayment.lifeTimeSeconds
          val fee = fees.get(currency).value
          val paymentHash = randomPaymentHash()

          when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
          when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
          when(paymentService.generatePaymentHash(currency)).thenReturn(Future.successful(paymentHash))
          when(channelsRepository.requestRentedChannelExtension(paymentHash, currency, channelId, fee, seconds))
            .thenReturn(())

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
          )

          val expected = WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GeneratePaymentHashToExtendConnextRentedChannelResponse(channelId, currency, seconds, paymentHash)
          )
          alice.client.expectMsg(expected)
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      s"fail when duration is more than 7 days for $currency" in {
        val channelsRepository = mock[ChannelsRepository.Blocking]

        withSinglePeer(channelsRepository = channelsRepository) { alice =>
          val requestId = "id"
          val expiresAt = Instant.now.plus(Duration.ofMinutes(15))
          val channel = SampleChannels
            .newConnextChannel()
            .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
          val channelId = channel.channelId
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val duration = 7.days.toSeconds + 1
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = duration)

          when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
          when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
          when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, duration)
          )

          val error = "Max duration is 7 days"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      s"return an error for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val channelId = ChannelId.ConnextChannelId.random()
          val seconds = 100000L

          alice.actor ! WebSocketIncomingMessage(
            requestId,
            GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val payingCurrency = Currency.ETH
        val seconds = 100000L
        val paymentHash = randomPaymentHash()

        when(tradesRepository.getLastPrice(TradingPair.ETH_BTC)).thenReturn(None)
        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.generatePaymentHash(payingCurrency)).thenReturn(Future.successful(paymentHash))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, payingCurrency, seconds)
        )

        val expected = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          GeneratePaymentHashToExtendConnextRentedChannelResponse(channelId, payingCurrency, seconds, paymentHash)
        )
        alice.client.expectMsg(expected)
      }
    }

    "fail when channel fee payment does not exist" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expiresAt = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(channelId)).thenThrow(new RuntimeException("Timeout"))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Timeout"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when payment hash could not be created" in {
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100000L

        when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)
        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.generatePaymentHash(currency)).thenReturn(
          Future.failed(new RuntimeException("Connection error"))
        )

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
        )

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Connection error"))
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100000L
        val fee = Helpers.asSatoshis("205.2")
        val paymentHash = randomPaymentHash()

        when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)
        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))
        when(channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId.value))).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(channelId)).thenReturn(Some(channelFeePayment))
        when(paymentService.generatePaymentHash(currency)).thenReturn(Future.successful(paymentHash))
        when(channelsRepository.requestRentedChannelExtension(paymentHash, currency, channelId, fee, seconds))
          .thenThrow(new RuntimeException("Connection refused"))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findConnextChannel(channelId)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Closed, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = None)
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
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
        val channel = SampleChannels
          .newConnextChannel()
          .copy(status = ConnextChannelStatus.Active, expiresAt = Some(expiresAt))
        val channelId = channel.channelId
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = Currency.USDC)
        val currency = channelFeePayment.payingCurrency
        val seconds = 100L

        when(channelsRepository.findConnextChannel(channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          GeneratePaymentHashToExtendConnextRentedChannel(channelId, currency, seconds)
        )

        val error = s"Channel $channelId expires in less than 10 minutes"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }
  }
}
