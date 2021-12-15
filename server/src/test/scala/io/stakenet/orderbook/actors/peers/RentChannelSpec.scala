package io.stakenet.orderbook.actors.peers

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.google.protobuf.ByteString
import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.RentChannel
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{CommandFailed, RentChannelResponse}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.lnd.channels.OpenChannelObserver
import io.stakenet.orderbook.models.clients.{ClientId, Identifier}
import io.stakenet.orderbook.models.connext.ConnextChannelContractDeploymentFee
import io.stakenet.orderbook.models.lnd.PaymentData
import io.stakenet.orderbook.models.{Channel, Currency, Preimage, Satoshis}
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.preimages.PreimagesRepository
import io.stakenet.orderbook.services.{ETHService, PaymentService}
import lnrpc.rpc.{OpenStatusUpdate, PendingUpdate}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar._
import org.mockito.{ArgumentCaptor, Mockito}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class RentChannelSpec extends PeerSpecBase("RentChannelSpec") {
  "RentChannel" should {
    "respond with RentChannelResponse(lnd)" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val lnd = mock[MulticurrencyLndClient]
      val paymentService = mock[PaymentService]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        lnd = lnd,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val outPoint = Helpers.randomOutpoint()
        val clientPublicKey = Helpers.randomClientPublicKey()
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val channel = SampleChannels.newChannel().copy(publicKey = clientPublicKey.key)
        val capacity = channelFeePayment.capacity

        when(clientsRepository.findPublicKey(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(
          Some(clientPublicKey)
        )
        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.XSN)).thenReturn(Some(channelFeePayment))
        when(paymentService.validatePayment(Currency.XSN, paymentRHash))
          .thenReturn(Future.successful(PaymentData(Satoshis.One, Instant.now)))
        when(channelsRepository.createChannel(any[Channel.LndChannel])).thenReturn(())
        when(
          lnd.openChannel(
            eqTo(Currency.BTC),
            eqTo(channel.publicKey),
            eqTo(capacity),
            any[OpenChannelObserver],
            any[Satoshis]
          )
        ).thenReturn(Future.unit)

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        onOpenChannel(lnd) { openChannelObserver =>
          val txid = ByteString.copyFrom(outPoint.txid.lndBytes)
          val pendingChannelUpdate = new PendingUpdate().withTxid(txid).withOutputIndex(outPoint.index)
          val statusUpdate = new OpenStatusUpdate().withChanPending(pendingChannelUpdate)

          openChannelObserver.onNext(statusUpdate)
        }

        alice.client.expectMsgPF(Duration(15.0, TimeUnit.SECONDS)) {
          case WebSocketOutgoingMessage(1, Some(requestIdReceived), response: RentChannelResponse) =>
            requestIdReceived must be(requestId)
            response.paymentHash must be(paymentRHash)
            response.channelIdentifier must be(outPoint)
            response.clientIdentifier must be(channel.publicKey)
        }
      }
    }

    "respond with RentChannelResponse(connext)" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val connext = mock[ConnextHelper]
      val preimageRepository = mock[PreimagesRepository.Blocking]
      val eth = mock[ETHService]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        clientsRepository = clientsRepository,
        connextHelper = connext,
        preimagesRepository = preimageRepository,
        ethService = eth
      ) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val clientPublicIdentifier = Helpers.randomClientPublicIdentifier()
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(currency = Currency.ETH)
        val address = Some(Helpers.randomChannelAddress())
        val capacity = channelFeePayment.capacity
        val contractFee = ConnextChannelContractDeploymentFee("hash2", ClientId.random(), Satoshis.One, Instant.now)
        val preimage = Preimage.random()

        when(clientsRepository.findPublicKey(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(None)
        when(clientsRepository.findPublicIdentifier(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(
          Some(clientPublicIdentifier)
        )
        when(channelsRepository.findConnextChannel(paymentRHash, Currency.ETH)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.ETH)).thenReturn(Some(channelFeePayment))
        when(channelsRepository.findConnextChannelContractDeploymentFee(any[ClientId])).thenReturn(Some(contractFee))

        when(preimageRepository.findPreimage(paymentRHash, Currency.ETH)).thenReturn(Some(preimage))
        when(connext.resolveTransfer(Currency.ETH, clientPublicIdentifier.identifier, paymentRHash, preimage))
          .thenReturn(Future.successful(Right(PaymentData(Satoshis.One, Instant.now))))

        when(connext.getCounterPartyChannelAddress(Currency.ETH, clientPublicIdentifier.identifier)).thenReturn(
          Future.successful(address)
        )
        when(connext.channelDeposit(address.value, capacity, Currency.ETH)).thenReturn(Future.successful("hash"))
        when(channelsRepository.createChannel(any[Channel.ConnextChannel], eqTo("hash"))).thenReturn(())

        when(eth.getLatestBlockNumber()).thenReturn(Future.successful(BigInt(50)))
        when(eth.getTransaction("hash")).thenReturn(
          Future.successful(ETHService.Transaction(BigInt(20), "address", Satoshis.One))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.ETH))

        alice.client.expectMsgPF(Duration(15.0, TimeUnit.SECONDS)) {
          case WebSocketOutgoingMessage(1, Some(requestIdReceived), response: RentChannelResponse) =>
            requestIdReceived must be(requestId)
            response.paymentHash must be(paymentRHash)
            response.clientIdentifier must be(clientPublicIdentifier.identifier)
        }
      }
    }

    "fail when lnd fails to open the channel" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val lnd = mock[MulticurrencyLndClient]
      val paymentService = mock[PaymentService]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        lnd = lnd,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val clientPublicKey = Helpers.randomClientPublicKey()
        val channelFeePayment = SampleChannels.newChannelFeePayment()
        val channel = SampleChannels.newChannel().copy(publicKey = clientPublicKey.key)
        val capacity = channelFeePayment.capacity

        when(clientsRepository.findPublicKey(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(
          Some(clientPublicKey)
        )
        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.XSN)).thenReturn(Some(channelFeePayment))
        when(paymentService.validatePayment(Currency.XSN, paymentRHash))
          .thenReturn(Future.successful(PaymentData(Satoshis.One, Instant.now)))
        when(channelsRepository.createChannel(any[Channel.LndChannel])).thenReturn(())
        when(
          lnd.openChannel(
            eqTo(Currency.BTC),
            eqTo(channel.publicKey),
            eqTo(capacity),
            any[OpenChannelObserver],
            any[Satoshis]
          )
        ).thenReturn(Future.unit)

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        onOpenChannel(lnd) { openChannelObserver =>
          openChannelObserver.onError(new RuntimeException("cant open channel"))
        }

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("cant open channel"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when the payment hash does not exist" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()

        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.XSN)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("The payment hash does not exist"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when the payment is not complete" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        paymentService = paymentService,
        channelsRepository = channelsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val channelFeePayment = SampleChannels.newChannelFeePayment()

        when(clientsRepository.findPublicKey(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(
          Some(Helpers.randomClientPublicKey())
        )
        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.XSN)).thenReturn(Some(channelFeePayment))
        when(paymentService.validatePayment(Currency.XSN, paymentRHash))
          .thenReturn(
            Future.failed(new RuntimeException("Invalid paymentHash, the invoice hasn't been settled"))
          )

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        val expected = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          CommandFailed("Invalid paymentHash, the invoice hasn't been settled")
        )
        alice.client.expectMsg(expected)
      }
    }

    "return RentChannelResponse when channel was already opened" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val outPoint = Helpers.randomOutpoint()
        val channel = SampleChannels.newChannel().withChannelPoint(outPoint)

        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        alice.client.expectMsgPF(Duration(15.0, TimeUnit.SECONDS)) {
          case WebSocketOutgoingMessage(1, Some(requestIdReceived), response: RentChannelResponse) =>
            requestIdReceived must be(requestId)
            response.paymentHash must be(paymentRHash)
            response.channelIdentifier must be(outPoint)
            response.clientIdentifier must be(channel.publicKey)
        }
      }
    }

    "fail when is in process of being opened" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val channel = SampleChannels.newChannel()

        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        val error = "Channel is already being opened"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }

    "fail when user does not have a public key registered" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository, clientsRepository = clientsRepository) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val channelFeePayment = SampleChannels.newChannelFeePayment()

        when(clientsRepository.findPublicKey(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(None)
        when(channelsRepository.findChannel(paymentRHash, Currency.XSN)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.XSN)).thenReturn(Some(channelFeePayment))

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.XSN))

        val error = s"You have no public identifier registered for ${channelFeePayment.currency}"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }

    "fail when contract deployment fee has not been paid" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository, clientsRepository = clientsRepository) { alice =>
        val requestId = "id"
        val paymentRHash = Helpers.randomPaymentHash()
        val clientPublicIdentifier = Helpers.randomClientPublicIdentifier()
        val channelFeePayment = SampleChannels.newChannelFeePayment().copy(currency = Currency.ETH)

        when(clientsRepository.findPublicKey(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(None)
        when(clientsRepository.findPublicIdentifier(any[ClientId], eqTo(channelFeePayment.currency))).thenReturn(
          Some(clientPublicIdentifier)
        )
        when(channelsRepository.findConnextChannel(paymentRHash, Currency.ETH)).thenReturn(None)
        when(channelsRepository.findChannelFeePayment(paymentRHash, Currency.ETH)).thenReturn(Some(channelFeePayment))
        when(channelsRepository.findConnextChannelContractDeploymentFee(any[ClientId])).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, RentChannel(paymentRHash, Currency.ETH))

        val error = "Channel contract fee not paid"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }
  }

  private def onOpenChannel(lndMock: MulticurrencyLndClient)(f: OpenChannelObserver => Unit): Unit = {
    val captor = ArgumentCaptor.forClass(classOf[OpenChannelObserver])
    val oneSecond = Mockito.timeout(1000)
    verify(lndMock, oneSecond).openChannel(
      any[Currency],
      any[Identifier.LndPublicKey],
      any[Satoshis],
      captor.capture(),
      any[Satoshis]
    )

    f(captor.getValue)

    Mockito.clearInvocations(lndMock)
  }
}
