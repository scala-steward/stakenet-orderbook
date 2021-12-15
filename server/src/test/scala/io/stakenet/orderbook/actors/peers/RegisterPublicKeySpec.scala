package io.stakenet.orderbook.actors.peers

import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.RegisterPublicKey
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{CommandFailed, RegisterPublicKeyResponse}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.clients.ClientIdentifier.ClientLndPublicKey
import io.stakenet.orderbook.models.clients.{ClientId, ClientPublicKeyId}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._

class RegisterPublicKeySpec extends PeerSpecBase("RegisterPublicKeySpec") {
  "RegisterPublicKey" should {
    Currency.forLnd.foreach { currency =>
      s"respond with RegisterPublicKeyResponse when client has no public key for $currency" in {
        val clientsRepository = mock[ClientsRepository.Blocking]

        withSinglePeer(clientsRepository = clientsRepository) { alice =>
          val publicKey = Helpers.randomPublicKey()

          when(clientsRepository.findPublicKey(any[ClientId], eqTo(currency))).thenReturn(None)

          alice.actor ! WebSocketIncomingMessage("id", RegisterPublicKey(currency, publicKey))

          alice.client.expectMsg(
            WebSocketOutgoingMessage(1, Some("id"), RegisterPublicKeyResponse(currency, publicKey))
          )
        }
      }

      s"respond with RegisterPublicKeyResponse when client has already registered the public key for $currency" in {
        val clientsRepository = mock[ClientsRepository.Blocking]

        withSinglePeer(clientsRepository = clientsRepository) { alice =>
          val clientPublicKey =
            ClientLndPublicKey(ClientPublicKeyId.random(), Helpers.randomPublicKey(), currency, ClientId.random())

          when(clientsRepository.findPublicKey(any[ClientId], eqTo(currency))).thenReturn(Some(clientPublicKey))

          alice.actor ! WebSocketIncomingMessage("id", RegisterPublicKey(currency, clientPublicKey.key))

          alice.client.expectMsg(
            WebSocketOutgoingMessage(1, Some("id"), RegisterPublicKeyResponse(currency, clientPublicKey.key))
          )
        }
      }

      s"fail when client has already registered another public key for $currency" in {
        val clientsRepository = mock[ClientsRepository.Blocking]

        withSinglePeer(clientsRepository = clientsRepository) { alice =>
          val clientPublicKey =
            ClientLndPublicKey(ClientPublicKeyId.random(), Helpers.randomPublicKey(), currency, ClientId.random())

          when(clientsRepository.findPublicKey(any[ClientId], eqTo(currency))).thenReturn(
            Some(clientPublicKey)
          )

          alice.actor ! WebSocketIncomingMessage("id", RegisterPublicKey(currency, Helpers.randomPublicKey()))

          val error = s"Client already has a different key(${clientPublicKey.key}) registered for $currency"
          alice.client.expectMsg(
            WebSocketOutgoingMessage(1, Some("id"), CommandFailed(error))
          )
        }
      }

      s"fail when clients repository returns an error for $currency" in {
        val clientsRepository = mock[ClientsRepository.Blocking]

        withSinglePeer(clientsRepository = clientsRepository) { alice =>
          val publicKey = Helpers.randomPublicKey()

          when(clientsRepository.findPublicKey(any[ClientId], eqTo(currency))).thenReturn(None)
          when(clientsRepository.registerPublicKey(any[ClientId], eqTo(publicKey), eqTo(currency))).thenThrow(
            new RuntimeException("error")
          )

          alice.actor ! WebSocketIncomingMessage("id", RegisterPublicKey(currency, publicKey))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed("error")))
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      s"fail for $currency" in {
        withSinglePeer() { alice =>
          val publicKey = Helpers.randomPublicKey()

          alice.actor ! WebSocketIncomingMessage("id", RegisterPublicKey(currency, publicKey))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed(s"$currency not supported")))
        }
      }
    }
  }
}
