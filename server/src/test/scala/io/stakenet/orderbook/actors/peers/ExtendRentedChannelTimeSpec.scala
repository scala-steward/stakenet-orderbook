package io.stakenet.orderbook.actors.peers
import java.time.{Duration, Instant}

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.ExtendRentedChannelTime
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse._
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models.{ChannelId, Currency, Satoshis}
import io.stakenet.orderbook.models.lnd.{ChannelStatus, PaymentData}
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.OptionValues._

import scala.concurrent.Future

class ExtendRentedChannelTimeSpec extends PeerSpecBase("ExtendRentedChannelTime") {
  "ExtendRentedChannelTime" should {
    "respond with ExtendRentedChannelTimeResponse" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(11))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency
        val extendedExpirationDate = channel.expiresAt.value.plusSeconds(channelExtension.seconds)
        val paymentData = PaymentData(Satoshis.One, Instant.now)

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(Future.successful(paymentData))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          ExtendRentedChannelTimeResponse(paymentHash, channel.channelId, extendedExpirationDate.getEpochSecond)
        )
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when extension request does not exist" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channelExtension = SampleChannels.newChannelExtension()
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(None)
        when(channelsRepository.findConnextChannelExtension(paymentHash, currency)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val error = s"Channel extension for $paymentHash in ${currency.entryName} was not found"
        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when channel does not exist" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channelId = ChannelId.LndChannelId.random()
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelId)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val error = s"Channel ${channelExtension.channelId} not found"
        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "respond with ExtendRentedChannelTimeResponse when extension request was already processed" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels
          .newChannelExtension()
          .copy(channelId = channel.channelId, paidAt = Some(Instant.now))
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency
        val paymentData = PaymentData(Satoshis.One, Instant.now)

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channel.channelId)).thenReturn(Some(channel))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(Future.successful(paymentData))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          ExtendRentedChannelTimeResponse(paymentHash, channel.channelId, channel.expiresAt.value.getEpochSecond)
        )
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when active channel has no expiration date" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active)
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val error = "Invalid state, active channel without expiration date"
        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when updating channel expiration date fails" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency
        val paymentData = PaymentData(Satoshis.One, Instant.now)

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Left("error"))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(Future.successful(paymentData))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("error"))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when channel extension cant be fetched for an unexpected error" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channelExtension = SampleChannels.newChannelExtension()
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenThrow(new RuntimeException("Timeout"))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Timeout"))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when channel cant be fetched for an unexpected error" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channelId = ChannelId.LndChannelId.random()
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelId)).thenThrow(new RuntimeException("Timeout"))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Timeout"))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when channel duration cant be extended for an unexpected error" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency
        val paymentData = PaymentData(Satoshis.One, Instant.now)

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(Future.successful(paymentData))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension))
          .thenThrow(new RuntimeException("Timeout"))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Timeout"))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when invoice status cant be verified for an unexpected error" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(30))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(
          Future.failed(new RuntimeException("Connection refused"))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Connection refused"))
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when channel expires in less than 10 minutes" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(5))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val error = s"Channel ${channel.channelId} expires in less than 10 minutes"
        val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expectedMessage)
      }
    }

    val invalidStatus = ChannelStatus.values.filter(_ != ChannelStatus.Active)
    invalidStatus.foreach { status =>
      s"fail when channel status is ${status.entryName}" in {
        val channelsRepository = mock[ChannelsRepository.Blocking]

        withSinglePeer(channelsRepository = channelsRepository) { alice =>
          val requestId = "id"
          val expirationDate = Instant.now.plus(Duration.ofMinutes(30))
          val channel = SampleChannels.newChannel().copy(status = status, expiresAt = Some(expirationDate))
          val channelExtension = SampleChannels.newChannelExtension().copy(channelId = channel.channelId)
          val paymentHash = channelExtension.paymentHash
          val currency = channelExtension.payingCurrency

          when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
          when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
          when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))

          alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

          val error = s"channel ${channel.channelId} is not active"
          val expectedMessage = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
          alice.client.expectMsg(expectedMessage)
        }
      }
    }

    "allow BTC payments when fee has more than 8 digits precision" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(11))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels
          .newChannelExtension()
          .copy(
            channelId = channel.channelId,
            payingCurrency = Currency.BTC,
            fee = Satoshis.from(BigDecimal("0.000000100166488555")).value
          )
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency
        val extendedExpirationDate = channel.expiresAt.value.plusSeconds(channelExtension.seconds)
        val paymentData = PaymentData(Satoshis.from(BigDecimal("0.00000010")).value, Instant.now)

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(Future.successful(paymentData))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          ExtendRentedChannelTimeResponse(paymentHash, channel.channelId, extendedExpirationDate.getEpochSecond)
        )
        alice.client.expectMsg(expectedMessage)
      }
    }

    "fail when fee does not match" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(channelsRepository = channelsRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val expirationDate = Instant.now.plus(Duration.ofMinutes(11))
        val channel = SampleChannels.newChannel().copy(status = ChannelStatus.Active, expiresAt = Some(expirationDate))
        val channelExtension = SampleChannels
          .newChannelExtension()
          .copy(
            channelId = channel.channelId,
            payingCurrency = Currency.BTC,
            fee = Satoshis.from(BigDecimal("0.000000100166488555")).value
          )
        val paymentHash = channelExtension.paymentHash
        val currency = channelExtension.payingCurrency
        val paymentData = PaymentData(Satoshis.from(BigDecimal("0.00000011")).value, Instant.now)

        when(channelsRepository.findChannelExtension(paymentHash, currency)).thenReturn(Some(channelExtension))
        when(channelsRepository.findChannel(channelExtension.channelId)).thenReturn(Some(channel))
        when(channelsRepository.payRentedChannelExtensionFee(channelExtension)).thenReturn(Right(()))
        when(paymentService.validatePayment(currency, paymentHash)).thenReturn(Future.successful(paymentData))

        alice.actor ! WebSocketIncomingMessage(requestId, ExtendRentedChannelTime(paymentHash, currency))

        val expectedMessage = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          CommandFailed("expected 0.00000010 BTC, got 0.00000011 BTC")
        )
        alice.client.expectMsg(expectedMessage)
      }
    }
  }
}
