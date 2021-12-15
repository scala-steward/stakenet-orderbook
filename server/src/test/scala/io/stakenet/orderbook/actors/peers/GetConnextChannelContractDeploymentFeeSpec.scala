package io.stakenet.orderbook.actors.peers

import java.time.Instant

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GetConnextChannelContractDeploymentFee
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  GetConnextChannelContractDeploymentFeeResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.connext.ConnextChannelContractDeploymentFee
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._

class GetConnextChannelContractDeploymentFeeSpec extends PeerSpecBase("GetBarsPricesSpec") {
  "RegisterConnextChannelContractDeploymentFee" should {
    val channelsRepository = mock[ChannelsRepository.Blocking]

    s"return the fee when client has not paid it yet" in {
      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"

        when(channelsRepository.findConnextChannelContractDeploymentFee(any[ClientId])).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, GetConnextChannelContractDeploymentFee())

        alice.client.expectMsg(
          WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GetConnextChannelContractDeploymentFeeResponse("hubAddress", Satoshis.from(BigDecimal("0.015")).value)
          )
        )
      }
    }

    s"return zero when client already paid the fee" in {
      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val feePayment = ConnextChannelContractDeploymentFee(
          transactionHash = "hash",
          clientId = ClientId.random(),
          Satoshis.One,
          createdAt = Instant.now
        )

        when(channelsRepository.findConnextChannelContractDeploymentFee(any[ClientId])).thenReturn(Some(feePayment))

        alice.actor ! WebSocketIncomingMessage(requestId, GetConnextChannelContractDeploymentFee())

        alice.client.expectMsg(
          WebSocketOutgoingMessage(
            1,
            Some(requestId),
            GetConnextChannelContractDeploymentFeeResponse("hubAddress", Satoshis.Zero)
          )
        )
      }
    }

    s"fail when fee payment could not be fetched" in {
      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"

        when(channelsRepository.findConnextChannelContractDeploymentFee(any[ClientId])).thenThrow(
          new RuntimeException("Connection Refused")
        )

        alice.actor ! WebSocketIncomingMessage(requestId, GetConnextChannelContractDeploymentFee())

        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Connection Refused")))
      }
    }
  }
}
