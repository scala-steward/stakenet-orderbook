package io.stakenet.orderbook.repositories.clients

import java.time.Instant

import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, Identifier}
import io.stakenet.orderbook.models.{Currency, Satoshis, WalletId}
import io.stakenet.orderbook.repositories.clients.ClientsRepository.Id
import javax.inject.Inject
import play.api.db.Database

class ClientsPostgresRepository @Inject()(database: Database) extends ClientsRepository.Blocking {

  override def createWalletClient(walletId: WalletId): ClientId = {
    database.withTransaction { implicit conn =>
      val clientId = ClientsDAO.createClient()
      ClientsDAO.createWalletClient(clientId, walletId)
    }
  }

  override def findWalletClient(walletId: WalletId): Option[WalletClient] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findWalletClient(walletId)
    }
  }

  override def findBotMakerClient(secret: String): Option[BotMakerClient] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findBotMakerClient(secret)
    }
  }

  override def registerPublicKey(clientId: ClientId, publicKey: Identifier.LndPublicKey, currency: Currency): Unit = {
    database.withConnection { implicit conn =>
      ClientsDAO.createClientPublicKey(clientId, publicKey, currency)
    }
  }

  override def registerPublicIdentifier(
      clientId: ClientId,
      publicIdentifier: ConnextPublicIdentifier,
      currency: Currency
  ): Unit = {
    database.withConnection { implicit conn =>
      ClientsDAO.createClientPublicIdentifier(clientId, publicIdentifier, currency)
    }
  }

  override def findPublicKey(clientId: ClientId, currency: Currency): Option[ClientLndPublicKey] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findPublicKey(clientId, currency)
    }
  }

  override def findPublicIdentifier(clientId: ClientId, currency: Currency): Option[ClientConnextPublicIdentifier] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findPublicIdentifier(clientId, currency)
    }
  }

  override def getAllClientIds(): Id[List[ClientId]] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findAllClientIds()
    }
  }

  override def getAllClientPublicKeys(clientId: ClientId): Id[List[ClientLndPublicKey]] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findAllClientPublicKeys(clientId)
    }
  }

  override def getPublicKeyRentedCapacity(clientPublicKey: ClientLndPublicKey): Id[Satoshis] = {
    database.withConnection { implicit conn =>
      ClientsDAO.getPublicKeyRentedCapacity(clientPublicKey)
    }
  }

  override def logClientInfo(
      clientId: ClientId,
      rentedCapacityUSD: BigDecimal,
      hubLocalBalanceUSD: BigDecimal,
      date: Instant
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      ClientsDAO.logClientInfo(clientId, rentedCapacityUSD, hubLocalBalanceUSD, date)
    }
  }

  override def findAllLndClientPublicKeys(currency: Currency): Id[List[ClientLndPublicKey]] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findAllLndClientPublicKeys(currency)
    }
  }

  override def findAllConnextClientPublicIdentifiers(currency: Currency): Id[List[ClientConnextPublicIdentifier]] = {
    database.withConnection { implicit conn =>
      ClientsDAO.findAllConnextClientPublicidentifiers(currency)
    }
  }
}
