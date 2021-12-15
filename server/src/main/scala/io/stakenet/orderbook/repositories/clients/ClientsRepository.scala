package io.stakenet.orderbook.repositories.clients

import java.time.Instant

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, Identifier}
import io.stakenet.orderbook.models.{Currency, Satoshis, WalletId}
import javax.inject.Inject

import scala.concurrent.Future

trait ClientsRepository[F[_]] {
  def createWalletClient(walletId: WalletId): F[ClientId]
  def findWalletClient(walletId: WalletId): F[Option[WalletClient]]
  def findBotMakerClient(secret: String): F[Option[BotMakerClient]]
  def registerPublicKey(clientId: ClientId, publicKey: Identifier.LndPublicKey, currency: Currency): F[Unit]

  def registerPublicIdentifier(
      clientId: ClientId,
      publicIdentifier: ConnextPublicIdentifier,
      currency: Currency
  ): F[Unit]
  def findPublicKey(clientId: ClientId, currency: Currency): F[Option[ClientLndPublicKey]]
  def findPublicIdentifier(clientId: ClientId, currency: Currency): F[Option[ClientConnextPublicIdentifier]]
  def getAllClientIds(): F[List[ClientId]]
  def getAllClientPublicKeys(clientId: ClientId): F[List[ClientLndPublicKey]]
  def getPublicKeyRentedCapacity(clientPublicKey: ClientLndPublicKey): F[Satoshis]

  def logClientInfo(
      clientId: ClientId,
      rentedCapacityUSD: BigDecimal,
      hubLocalBalanceUSD: BigDecimal,
      date: Instant
  ): F[Unit]

  def findAllLndClientPublicKeys(currency: Currency): F[List[ClientLndPublicKey]]
  def findAllConnextClientPublicIdentifiers(currency: Currency): F[List[ClientConnextPublicIdentifier]]
}

object ClientsRepository {

  type Id[T] = T
  trait Blocking extends ClientsRepository[Id]

  class FutureImpl @Inject()(blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends ClientsRepository[scala.concurrent.Future] {

    override def createWalletClient(walletId: WalletId): Future[ClientId] = Future {
      blocking.createWalletClient(walletId)
    }

    override def findWalletClient(walletId: WalletId): Future[Option[WalletClient]] = Future {
      blocking.findWalletClient(walletId)
    }

    override def findBotMakerClient(secret: String): Future[Option[BotMakerClient]] = Future {
      blocking.findBotMakerClient(secret)
    }

    override def registerPublicKey(
        clientId: ClientId,
        publicKey: Identifier.LndPublicKey,
        currency: Currency
    ): Future[Unit] =
      Future {
        blocking.registerPublicKey(clientId, publicKey, currency)
      }

    override def registerPublicIdentifier(
        clientId: ClientId,
        publicIdentifier: ConnextPublicIdentifier,
        currency: Currency
    ): Future[Unit] = Future {
      blocking.registerPublicIdentifier(clientId, publicIdentifier, currency)
    }

    override def findPublicKey(clientId: ClientId, currency: Currency): Future[Option[ClientLndPublicKey]] = Future {
      blocking.findPublicKey(clientId, currency)
    }

    override def findPublicIdentifier(
        clientId: ClientId,
        currency: Currency
    ): Future[Option[ClientConnextPublicIdentifier]] =
      Future {
        blocking.findPublicIdentifier(clientId, currency)
      }

    override def getAllClientIds(): Future[List[ClientId]] = Future {
      blocking.getAllClientIds()
    }

    override def getAllClientPublicKeys(clientId: ClientId): Future[List[ClientLndPublicKey]] = Future {
      blocking.getAllClientPublicKeys(clientId)
    }

    override def getPublicKeyRentedCapacity(clientPublicKey: ClientLndPublicKey): Future[Satoshis] = Future {
      blocking.getPublicKeyRentedCapacity(clientPublicKey)
    }

    override def logClientInfo(
        clientId: ClientId,
        rentedCapacityUSD: BigDecimal,
        hubLocalBalanceUSD: BigDecimal,
        date: Instant
    ): Future[Unit] = Future {
      blocking.logClientInfo(clientId, rentedCapacityUSD, hubLocalBalanceUSD, date)
    }

    override def findAllLndClientPublicKeys(currency: Currency): Future[List[ClientLndPublicKey]] = Future {
      blocking.findAllLndClientPublicKeys(currency)

    }

    override def findAllConnextClientPublicIdentifiers(
        currency: Currency
    ): Future[List[ClientConnextPublicIdentifier]] =
      Future {
        blocking.findAllConnextClientPublicIdentifiers(currency)
      }
  }
}
