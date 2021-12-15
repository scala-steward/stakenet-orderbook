package io.stakenet.orderbook.services

import helpers.Helpers
import io.stakenet.orderbook.actors.peers.PeerUser
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd.HubLocalBalances
import io.stakenet.orderbook.models.{AuthenticationInformation, Currency, Satoshis, WalletId}
import io.stakenet.orderbook.repositories.clients
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.services
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class ClientServiceSpec extends AsyncWordSpec with Matchers {

  "getUser" should {
    "get web user" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = None,
        websocketSubprotocol = Some("something")
      )

      val result = service.getUser(authenticationInformation)

      result.map {
        case Some(user: PeerUser.WebOrderbook.type) =>
          user.name mustBe "WebOrderbook"
          user.maxAllowedOrders mustBe 0
        case r =>
          fail(s"Expected Some(PeerUser), got $r")

      }
    }

    "get bot maker user" in {
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)
      val client = BotMakerClient("bot1.xsnbot.com", ClientId.random(), false)
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = Some("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"),
        walletId = None,
        websocketSubprotocol = None
      )

      when(clientsRepository.findBotMakerClient("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"))
        .thenReturn(Some(client))

      val result = service.getUser(authenticationInformation)

      result.map {
        case Some(user: PeerUser.Bot) =>
          user.name mustBe "bot1.xsnbot.com"
          user.maxAllowedOrders mustBe 1000
          user.paysFees mustBe false
        case r =>
          fail(s"Expected Some(PeerUser), got $r")

      }
    }

    "get wallet user" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = Some("048d669299fba67ddbbcfa86fb3a344d0d3a5066"),
        websocketSubprotocol = None
      )

      val result = service.getUser(authenticationInformation)

      result.map {
        case Some(user: PeerUser.Wallet) =>
          user.name mustBe "048d669299fba67ddbbcfa86fb3a344d0d3a5066"
          user.maxAllowedOrders mustBe 100
        case r =>
          fail(s"Expected Some(PeerUser), got $r")

      }
    }

    "fail when authentication is provided for a bot user, a wallet user and a web client" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = Some("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"),
        walletId = Some("048d669299fba67ddbbcfa86fb3a344d0d3a5066"),
        websocketSubprotocol = Some("something")
      )

      val result = service.getUser(authenticationInformation)

      result.map { _ mustBe None }
    }

    "return wallet user when authentication is provided for a wallet user and a web client" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = Some("048d669299fba67ddbbcfa86fb3a344d0d3a5066"),
        websocketSubprotocol = Some("something")
      )

      val result = service.getUser(authenticationInformation)

      result.map {
        case Some(user: PeerUser.Wallet) =>
          user.name mustBe "048d669299fba67ddbbcfa86fb3a344d0d3a5066"
          user.maxAllowedOrders mustBe 100
        case r =>
          fail(s"Expected Some(PeerUser), got $r")

      }
    }

    "return bot user when authentication is provided for a bot user and a web client" in {
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)
      val client = BotMakerClient("bot1.xsnbot.com", ClientId.random(), false)
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = Some("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"),
        walletId = None,
        websocketSubprotocol = Some("something")
      )

      when(clientsRepository.findBotMakerClient("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"))
        .thenReturn(Some(client))

      val result = service.getUser(authenticationInformation)

      result.map {
        case Some(user: PeerUser.Bot) =>
          user.name mustBe "bot1.xsnbot.com"
          user.maxAllowedOrders mustBe 1000
          user.paysFees mustBe false
        case r =>
          fail(s"Expected Some(PeerUser), got $r")

      }
    }

    "fail when authentication is provided for a bot user and a wallet user" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = Some("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"),
        walletId = Some("048d669299fba67ddbbcfa86fb3a344d0d3a5066"),
        websocketSubprotocol = None
      )

      val result = service.getUser(authenticationInformation)

      result.map { _ mustBe None }
    }

    "return None for empty headers" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = None,
        websocketSubprotocol = None
      )

      val result = service.getUser(authenticationInformation)

      result.map(_ mustBe None)
    }

    "return None for bot maker that does not exist" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = Some("nope"),
        walletId = None,
        websocketSubprotocol = None
      )

      val result = service.getUser(authenticationInformation)

      result.map(_ mustBe None)
    }

    "return None for invalid wallet id" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = Some("nope"),
        websocketSubprotocol = None
      )

      val result = service.getUser(authenticationInformation)

      result.map(_ mustBe None)
    }

    "fail when bot maker does not exist in the database" in {
      val service = getService()
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = Some("nope"),
        walletId = None,
        websocketSubprotocol = None
      )

      service.getUser(authenticationInformation).map { _ mustBe None }
    }

    "store new wallet user" in {
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)
      val walletId = WalletId("048d669299fba67ddbbcfa86fb3a344d0d3a5066").value
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = Some(walletId.toString),
        websocketSubprotocol = None
      )

      when(clientsRepository.findWalletClient(walletId)).thenReturn(None)
      when(clientsRepository.createWalletClient(walletId))
        .thenReturn(ClientId.random())

      service.getUser(authenticationInformation).map { _ =>
        verify(clientsRepository).createWalletClient(walletId)
        succeed
      }
    }

    "not store existing wallet user" in {
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)
      val walletId = WalletId("048d669299fba67ddbbcfa86fb3a344d0d3a5066").value
      val client = WalletClient(walletId, ClientId.random())
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = Some(walletId.toString),
        websocketSubprotocol = None
      )

      when(clientsRepository.findWalletClient(walletId)).thenReturn(Some(client))

      service.getUser(authenticationInformation).map { _ =>
        verify(clientsRepository, never).createWalletClient(any[WalletId])
        succeed
      }
    }

    "not store web users" in {
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)
      val authenticationInformation = AuthenticationInformation(
        botMakerSecret = None,
        walletId = None,
        websocketSubprotocol = Some("something")
      )

      service.getUser(authenticationInformation).map { _ =>
        verify(clientsRepository, never).createWalletClient(any[WalletId])
        succeed
      }
    }
  }

  "getClientHubLocalBalanceUSD" should {
    "return the correct amount" in {
      val clientId = ClientId.random()
      val xsnKey = Helpers.randomClientPublicKey(Currency.XSN)
      val btcKey = Helpers.randomClientPublicKey(Currency.BTC)
      val clientsRepository = mock[ClientsPostgresRepository]
      val usdConverter = mock[UsdConverter]
      val service = getService(clientsRepository, usdConverter)
      val xsnHubLocalBalance = Satoshis.Zero
      val btcHubLocalBalance = Satoshis.from(BigDecimal(2)).value
      val hubLocalBalances = Map[Currency, HubLocalBalances](
        btcKey.currency -> HubLocalBalances.empty(btcKey.currency).add(btcKey.key.toString, btcHubLocalBalance)
      )

      when(clientsRepository.getAllClientPublicKeys(clientId)).thenReturn(List(xsnKey, btcKey))
      when(usdConverter.convert(btcHubLocalBalance, Currency.BTC)).thenReturn(Future.successful(Right(4800)))
      when(usdConverter.convert(xsnHubLocalBalance, Currency.XSN)).thenReturn(Future.successful(Right(0)))

      service.getClientHubLocalBalanceUSD(clientId, hubLocalBalances).map { result =>
        result mustBe 4800
      }
    }

    "return zero when client has no public keys" in {
      val clientId = ClientId.random()
      val clientsRepository = mock[ClientsPostgresRepository]
      val usdConverter = mock[UsdConverter]
      val service = getService(clientsRepository, usdConverter)
      val hubLocalBalances = Map[Currency, HubLocalBalances](
        Currency.BTC -> HubLocalBalances.empty(Currency.BTC).add(Helpers.randomPublicKey().toString, Satoshis.One)
      )

      when(clientsRepository.getAllClientPublicKeys(clientId)).thenReturn(List.empty)

      service.getClientHubLocalBalanceUSD(clientId, hubLocalBalances).map { result =>
        result mustBe 0
      }
    }

    "return 0 when client has no locked balance on the hub" in {
      val clientId = ClientId.random()
      val xsnKey = Helpers.randomClientPublicKey(Currency.XSN)
      val btcKey = Helpers.randomClientPublicKey(Currency.BTC)
      val clientsRepository = mock[ClientsPostgresRepository]
      val usdConverter = mock[UsdConverter]
      val service = getService(clientsRepository, usdConverter)
      val hubLocalBalances = Map[Currency, HubLocalBalances](
        btcKey.currency -> HubLocalBalances.empty(btcKey.currency)
      )

      when(clientsRepository.getAllClientPublicKeys(clientId)).thenReturn(List(xsnKey, btcKey))
      when(usdConverter.convert(Satoshis.Zero, Currency.BTC)).thenReturn(Future.successful(Right(0)))
      when(usdConverter.convert(Satoshis.Zero, Currency.XSN)).thenReturn(Future.successful(Right(0)))

      service.getClientHubLocalBalanceUSD(clientId, hubLocalBalances).map { result =>
        result mustBe 0
      }
    }
  }

  "getClientRentedCapacityUSD" should {
    "return the correct amount" in {
      val clientId = ClientId.random()
      val xsnKey = Helpers.randomClientPublicKey(Currency.XSN)
      val btcKey = Helpers.randomClientPublicKey(Currency.BTC)
      val clientsRepository = mock[ClientsPostgresRepository]
      val usdConverter = mock[UsdConverter]
      val service = getService(clientsRepository, usdConverter)
      val xsnRentedCapacity = Satoshis.from(BigDecimal(100)).value
      val btcRentedCapacity = Satoshis.Zero

      when(clientsRepository.getAllClientPublicKeys(clientId)).thenReturn(List(xsnKey, btcKey))
      when(clientsRepository.getPublicKeyRentedCapacity(xsnKey)).thenReturn(xsnRentedCapacity)
      when(clientsRepository.getPublicKeyRentedCapacity(btcKey)).thenReturn(btcRentedCapacity)
      when(usdConverter.convert(xsnRentedCapacity, Currency.XSN)).thenReturn(Future.successful(Right(5000)))
      when(usdConverter.convert(btcRentedCapacity, Currency.BTC)).thenReturn(Future.successful(Right(0)))

      service.getClientRentedCapacityUSD(clientId).map { result =>
        result mustBe 5000
      }
    }

    "return 0 when client has no public keys" in {
      val clientId = ClientId.random()
      val clientsRepository = mock[ClientsPostgresRepository]
      val usdConverter = mock[UsdConverter]
      val service = getService(clientsRepository, usdConverter)

      when(clientsRepository.getAllClientPublicKeys(clientId)).thenReturn(List.empty)

      service.getClientRentedCapacityUSD(clientId).map { result =>
        result mustBe 0
      }
    }
  }

  "findClientIdentifier" should {
    "return a public key for an lnd currency" in {
      val clientId = ClientId.random()
      val publicKey = Helpers.randomClientPublicKey(Currency.XSN)
      val publicIdentifier = Helpers.randomClientPublicIdentifier(Currency.USDT)
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)

      when(clientsRepository.findPublicKey(clientId, Currency.XSN)).thenReturn(Some(publicKey))
      when(clientsRepository.findPublicIdentifier(clientId, Currency.USDT)).thenReturn(Some(publicIdentifier))

      service.findClientIdentifier(clientId, Currency.XSN).map { result =>
        result mustBe Some(publicKey)
      }
    }

    "return a public identifier for a connext currency" in {
      val clientId = ClientId.random()
      val publicKey = Helpers.randomClientPublicKey(Currency.XSN)
      val publicIdentifier = Helpers.randomClientPublicIdentifier(Currency.USDT)
      val clientsRepository = mock[ClientsPostgresRepository]
      val service = getService(clientsRepository)

      when(clientsRepository.findPublicKey(clientId, Currency.XSN)).thenReturn(Some(publicKey))
      when(clientsRepository.findPublicIdentifier(clientId, Currency.USDT)).thenReturn(Some(publicIdentifier))

      service.findClientIdentifier(clientId, Currency.USDT).map { result =>
        result mustBe Some(publicIdentifier)
      }
    }
  }

  private def getService(): ClientService = {
    val clientsRepository = mock[ClientsPostgresRepository]
    when(clientsRepository.findBotMakerClient(any[String])).thenReturn(None)
    when(clientsRepository.findWalletClient(any[WalletId])).thenReturn(None)
    when(clientsRepository.createWalletClient(any[WalletId])).thenReturn(ClientId.random())

    getService(clientsRepository, mock[UsdConverter])
  }

  private def getService(
      clientsRepository: ClientsPostgresRepository,
      usdConverter: UsdConverter = mock[UsdConverter]
  ): ClientService = {
    val clientsRepositoryAsync = new clients.ClientsRepository.FutureImpl(clientsRepository)(Executors.databaseEC)
    new services.ClientService.ClientServiceImpl(clientsRepositoryAsync, usdConverter)(Executors.globalEC)
  }
}
