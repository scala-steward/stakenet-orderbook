package io.stakenet.orderbook.actors.peers

import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.RegisterPublicIdentifier
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{
  CommandFailed,
  RegisterPublicIdentifierResponse
}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.clients.ClientIdentifier.ClientConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, ClientPublicIdentifierId}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._

class RegisterPublicIdentifierSpec extends PeerSpecBase("RegisterPublicIdentifierSpec") {
  "RegisterPublicIdentifier" should {
    s"respond with RegisterPublicIdentifierResponse when client has no public identifier" in {
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(clientsRepository = clientsRepository) { alice =>
        val currency = Currency.WETH
        val publicIdentifier = Helpers.randomPublicIdentifier()

        when(clientsRepository.findPublicIdentifier(any[ClientId], eqTo(currency))).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage("id", RegisterPublicIdentifier(currency, publicIdentifier))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some("id"), RegisterPublicIdentifierResponse(currency, publicIdentifier))
        )
      }
    }

    s"respond with RegisterPublicIdentifierResponse when client has already registered the public identifier" in {
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(clientsRepository = clientsRepository) { alice =>
        val currency = Currency.WETH
        val publicIdentifier = Helpers.randomPublicIdentifier()
        val clientPublicIdentifier =
          ClientConnextPublicIdentifier(
            ClientPublicIdentifierId.random(),
            publicIdentifier,
            currency,
            ClientId.random()
          )

        when(clientsRepository.findPublicIdentifier(any[ClientId], eqTo(currency))).thenReturn(
          Some(clientPublicIdentifier)
        )

        alice.actor ! WebSocketIncomingMessage("id", RegisterPublicIdentifier(currency, publicIdentifier))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some("id"), RegisterPublicIdentifierResponse(currency, publicIdentifier))
        )
      }
    }

    s"fail when client has already registered another public identifier" in {
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(clientsRepository = clientsRepository) { alice =>
        val currency = Currency.WETH
        val publicIdentifier = Helpers.randomPublicIdentifier()
        val clientPublicIdentifier =
          ClientConnextPublicIdentifier(
            ClientPublicIdentifierId.random(),
            publicIdentifier,
            currency,
            ClientId.random()
          )

        when(clientsRepository.findPublicIdentifier(any[ClientId], eqTo(currency))).thenReturn(
          Some(clientPublicIdentifier)
        )

        alice.actor ! WebSocketIncomingMessage(
          "id",
          RegisterPublicIdentifier(currency, Helpers.randomPublicIdentifier())
        )

        val error = s"Client already has a different identifier($publicIdentifier) registered for $currency"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed(error)))
      }
    }

    s"fail when clients repository returns an error" in {
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(clientsRepository = clientsRepository) { alice =>
        val currency = Currency.WETH
        val publicIdentifier = Helpers.randomPublicIdentifier()

        when(clientsRepository.findPublicIdentifier(any[ClientId], eqTo(currency))).thenReturn(None)
        when(clientsRepository.registerPublicIdentifier(any[ClientId], eqTo(publicIdentifier), eqTo(currency)))
          .thenThrow(new RuntimeException("error"))

        alice.actor ! WebSocketIncomingMessage("id", RegisterPublicIdentifier(currency, publicIdentifier))

        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed("error")))
      }
    }
  }
}
