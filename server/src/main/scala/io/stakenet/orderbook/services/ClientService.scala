package io.stakenet.orderbook.services

import java.time.Instant

import io.stakenet.orderbook.actors.peers.PeerUser
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, ClientIdentifier, Identifier}
import io.stakenet.orderbook.models.lnd.HubLocalBalances
import io.stakenet.orderbook.models.{AuthenticationInformation, Currency, Satoshis, WalletId}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

trait ClientService {

  def getUser(authenticationInformation: AuthenticationInformation): Future[Option[PeerUser]]
  def registerPublicKey(clientId: ClientId, publicKey: Identifier.LndPublicKey, currency: Currency): Future[Unit]

  def registerPublicIdentifier(
      clientId: ClientId,
      publicIdentifier: ConnextPublicIdentifier,
      currency: Currency
  ): Future[Unit]
  def findPublicKey(clientId: ClientId, currency: Currency): Future[Option[ClientLndPublicKey]]
  def findPublicIdentifier(clientId: ClientId, currency: Currency): Future[Option[ClientConnextPublicIdentifier]]
  def findClientIdentifier(clientId: ClientId, currency: Currency): Future[Option[ClientIdentifier]]
  def getAllClientIds(): Future[List[ClientId]]
  def getClientPublicKeys(clientId: ClientId): Future[List[ClientLndPublicKey]]
  def getPublicKeyRentedCapacity(clientPublicKey: ClientLndPublicKey): Future[Satoshis]
  def logClientStatus(clientId: ClientId, rentedCapacityUSD: BigDecimal, hubLocalBalanceUSD: BigDecimal): Future[Unit]

  def getClientHubLocalBalanceUSD(
      clientId: ClientId,
      hubChannelsLocalBalance: Map[Currency, HubLocalBalances]
  ): Future[BigDecimal]

  def getClientRentedCapacityUSD(clientId: ClientId): Future[BigDecimal]
}

object ClientService {

  class ClientServiceImpl @Inject() (clientsRepository: ClientsRepository.FutureImpl, usdConverter: UsdConverter)(
      implicit ec: ExecutionContext
  ) extends ClientService {

    override def getUser(authenticationInformation: AuthenticationInformation): Future[Option[PeerUser]] = {
      val botMakerSecret = authenticationInformation.botMakerSecret
      val walletId = authenticationInformation.walletId.flatMap(WalletId.apply)
      val websocketSubprotocol = authenticationInformation.websocketSubprotocol

      (botMakerSecret, walletId, websocketSubprotocol) match {
        case (Some(secret), None, _) =>
          val botMakerAllowedOrders = 1000

          clientsRepository.findBotMakerClient(secret).map {
            case Some(BotMakerClient(name, clientId, paysFees)) =>
              Some(PeerUser.Bot(clientId, paysFees, name, botMakerAllowedOrders))
            case None =>
              None
          }
        case (None, Some(walletId), _) =>
          val walletUserAllowedOrders = 100

          clientsRepository.findWalletClient(walletId).flatMap {
            case Some(WalletClient(walletId, clientId)) =>
              Future.successful(Some(PeerUser.Wallet(clientId, walletId.toString, walletUserAllowedOrders)))
            case None =>
              clientsRepository
                .createWalletClient(walletId)
                .map(id => Some(PeerUser.Wallet(id, walletId.toString, walletUserAllowedOrders)))
          }
        case (None, None, Some(_)) =>
          Future.successful(Some(PeerUser.WebOrderbook))
        case _ =>
          Future.successful(None)
      }
    }

    override def registerPublicKey(
        clientId: ClientId,
        publicKey: Identifier.LndPublicKey,
        currency: Currency
    ): Future[Unit] = {
      clientsRepository.registerPublicKey(clientId, publicKey, currency)
    }

    override def registerPublicIdentifier(
        clientId: ClientId,
        publicIdentifier: ConnextPublicIdentifier,
        currency: Currency
    ): Future[Unit] = {
      clientsRepository.registerPublicIdentifier(clientId, publicIdentifier, currency)
    }

    override def findPublicKey(clientId: ClientId, currency: Currency): Future[Option[ClientLndPublicKey]] = {
      clientsRepository.findPublicKey(clientId, currency)
    }

    override def findPublicIdentifier(
        clientId: ClientId,
        currency: Currency
    ): Future[Option[ClientConnextPublicIdentifier]] = {
      clientsRepository.findPublicIdentifier(clientId, currency)
    }

    override def findClientIdentifier(clientId: ClientId, currency: Currency): Future[Option[ClientIdentifier]] = {
      if (Currency.forLnd.contains(currency)) {
        findPublicKey(clientId, currency)
      } else {
        findPublicIdentifier(clientId, currency)
      }
    }

    override def getAllClientIds(): Future[List[ClientId]] = {
      clientsRepository.getAllClientIds()
    }

    override def getClientPublicKeys(clientId: ClientId): Future[List[ClientLndPublicKey]] = {
      clientsRepository.getAllClientPublicKeys(clientId)
    }

    override def getPublicKeyRentedCapacity(clientPublicKey: ClientLndPublicKey): Future[Satoshis] = {
      clientsRepository.getPublicKeyRentedCapacity(clientPublicKey)
    }

    override def logClientStatus(
        clientId: ClientId,
        rentedCapacityUSD: BigDecimal,
        hubLocalBalanceUSD: BigDecimal
    ): Future[Unit] = {
      val now = Instant.now

      clientsRepository.logClientInfo(clientId, rentedCapacityUSD, hubLocalBalanceUSD, now)
    }

    def getClientHubLocalBalanceUSD(
        clientId: ClientId,
        hubChannelsLocalBalance: Map[Currency, HubLocalBalances]
    ): Future[BigDecimal] = {
      for {
        clientPublicKeys <- getClientPublicKeys(clientId)
        usdBalances <- Future.sequence(
          clientPublicKeys.map(getPublicKeyHubLocalBalanceUSD(_, hubChannelsLocalBalance))
        )
      } yield usdBalances.sum
    }

    def getClientRentedCapacityUSD(clientId: ClientId): Future[BigDecimal] = {
      for {
        clientPublicKeys <- getClientPublicKeys(clientId)
        usdCapacities <- Future.sequence(clientPublicKeys.map(getPublicKeyRentedCapacityUSD))
      } yield usdCapacities.sum
    }

    private def getPublicKeyRentedCapacityUSD(clientPublicKey: ClientLndPublicKey): Future[BigDecimal] = {
      for {
        rentedCapacity <- getPublicKeyRentedCapacity(clientPublicKey)
        usdRentedCapacity <- usdConverter.convert(rentedCapacity, clientPublicKey.currency)
      } yield usdRentedCapacity match {
        case Right(usdRentedCapacity) => usdRentedCapacity
        case Left(error) => throw new RuntimeException(error.toString)
      }
    }

    private def getPublicKeyHubLocalBalanceUSD(
        clientPublicKey: ClientLndPublicKey,
        hubChannelsLocalBalance: Map[Currency, HubLocalBalances]
    ): Future[BigDecimal] = {
      val currency = clientPublicKey.currency
      val publicKey = clientPublicKey.key.toString
      val currencyLocalBalances = hubChannelsLocalBalance.getOrElse(currency, HubLocalBalances.empty(currency))
      val clientBalance = currencyLocalBalances.get(publicKey)

      usdConverter.convert(clientBalance, currency).map {
        case Right(usdBalance) => usdBalance
        case Left(error) => throw new RuntimeException(error.toString)
      }
    }
  }
}
