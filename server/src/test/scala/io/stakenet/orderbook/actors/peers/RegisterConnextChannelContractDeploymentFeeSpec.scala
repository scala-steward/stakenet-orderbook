package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.RegisterConnextChannelContractDeploymentFee
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  RegisterConnextChannelContractDeploymentFeeResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.services.ExplorerService
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._

import scala.concurrent.Future

class RegisterConnextChannelContractDeploymentFeeSpec extends PeerSpecBase("GetBarsPricesSpec") {
  "RegisterConnextChannelContractDeploymentFee" should {
    val channelsRepository = mock[ChannelsRepository.Blocking]
    val ethService = mock[ExplorerService]

    s"register the fee payment" in {
      withSinglePeer(channelsRepository = channelsRepository, explorerService = ethService) { alice =>
        val requestId = "id"
        val transactionHash = "hash"
        val transaction = ExplorerService.Transaction(
          blockNumber = BigInt(20),
          to = "hubAddress",
          value = Satoshis.from(BigDecimal("0.015")).value
        )

        when(ethService.getTransaction(Currency.ETH, transactionHash)).thenReturn(Future.successful(Right(transaction)))
        when(
          channelsRepository.createConnextChannelContractDeploymentFee(
            eqTo(transactionHash),
            any[ClientId],
            eqTo(transaction.value)
          )
        ).thenReturn(())

        alice.actor ! WebSocketIncomingMessage(requestId, RegisterConnextChannelContractDeploymentFee(transactionHash))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(
            1,
            Some(requestId),
            RegisterConnextChannelContractDeploymentFeeResponse(transactionHash)
          )
        )
      }
    }

    s"fail when transaction is not paid to the hub address" in {
      withSinglePeer(channelsRepository = channelsRepository, explorerService = ethService) { alice =>
        val requestId = "id"
        val transactionHash = "hash"
        val transaction = ExplorerService.Transaction(
          blockNumber = BigInt(20),
          to = "notHubAddress",
          value = Satoshis.from(BigDecimal("0.015")).value
        )

        when(ethService.getTransaction(Currency.ETH, transactionHash)).thenReturn(Future.successful(Right(transaction)))

        alice.actor ! WebSocketIncomingMessage(requestId, RegisterConnextChannelContractDeploymentFee(transactionHash))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Transaction must be paid to hubAddress"))
        )
      }
    }

    s"fail when paid fee does not match expected fee" in {
      withSinglePeer(channelsRepository = channelsRepository, explorerService = ethService) { alice =>
        val requestId = "id"
        val transactionHash = "hash"
        val transaction = ExplorerService.Transaction(
          blockNumber = BigInt(20),
          to = "hubAddress",
          value = Satoshis.from(BigDecimal("0.0151")).value
        )

        when(ethService.getTransaction(Currency.ETH, transactionHash)).thenReturn(Future.successful(Right(transaction)))

        alice.actor ! WebSocketIncomingMessage(requestId, RegisterConnextChannelContractDeploymentFee(transactionHash))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(
            1,
            Some(requestId),
            CommandFailed("expected 0.015000000000000000, got 0.015100000000000000")
          )
        )
      }
    }

    s"fail when repository fails" in {
      withSinglePeer(channelsRepository = channelsRepository, explorerService = ethService) { alice =>
        val requestId = "id"
        val transactionHash = "hash"
        val transaction = ExplorerService.Transaction(
          blockNumber = BigInt(20),
          to = "hubAddress",
          value = Satoshis.from(BigDecimal("0.015")).value
        )

        when(ethService.getTransaction(Currency.ETH, transactionHash)).thenReturn(Future.successful(Right(transaction)))
        when(
          channelsRepository.createConnextChannelContractDeploymentFee(
            eqTo(transactionHash),
            any[ClientId],
            eqTo(transaction.value)
          )
        ).thenThrow(new RuntimeException("Connection Refused"))

        alice.actor ! WebSocketIncomingMessage(
          requestId,
          RegisterConnextChannelContractDeploymentFee(transactionHash)
        )

        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Connection Refused"))
        )
      }
    }
  }
}
