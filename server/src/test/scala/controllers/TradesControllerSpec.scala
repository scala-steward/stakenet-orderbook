package controllers

import common.MyAPISpec
import helpers.Helpers
import helpers.Helpers.{randomClientPublicIdentifier, randomClientPublicKey, randomOutpoint, randomPublicKey}
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.lnd.impl.MulticurrencyLndDefaultImpl
import io.stakenet.orderbook.models.clients.ClientIdentifier.ClientLndPublicKey
import io.stakenet.orderbook.models.clients.ClientPublicKeyId
import io.stakenet.orderbook.models.lnd.OpenChannel
import io.stakenet.orderbook.models.trading.{TradingPair, TradingPairVolume}
import io.stakenet.orderbook.models.{Currency, Satoshis, connext}
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.trades.TradesPostgresRepository
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.inject.bind
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}

import scala.concurrent.Future

class TradesControllerSpec extends MyAPISpec {
  val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)
  val tradesPostgresRepository: TradesPostgresRepository = mock[TradesPostgresRepository]
  val clientsPostgresRepository: ClientsPostgresRepository = mock[ClientsPostgresRepository]
  val multicurrencyLndDefaultImpl: MulticurrencyLndDefaultImpl = mock[MulticurrencyLndDefaultImpl]
  val connextHelper: ConnextHelper = mock[ConnextHelper]

  "GET /trading-pairs/:tradingPair/volume" should {
    def url(tradingPair: TradingPair, lastDays: Int) = s"/trading-pairs/$tradingPair/volume?lastDays=$lastDays"

    "return the volume for a given trading pair for the last day" in {
      val days = 1
      val btcVolume = BigDecimal(1.09090909)
      val usdVolume = BigDecimal(12000)

      when(tradesPostgresRepository.getVolume(TradingPair.XSN_BTC, days)).thenReturn(
        TradingPairVolume(TradingPair.XSN_BTC, Satoshis.from(btcVolume).value, Satoshis.from(usdVolume).value)
      )

      val response = GET(url(TradingPair.XSN_BTC, days))
      status(response) mustEqual OK
      val json = contentAsJson(response)

      (json \ "pair").as[TradingPair] mustEqual TradingPair.XSN_BTC
      (json \ "volumeBTC").as[BigDecimal] mustEqual btcVolume
      (json \ "volumeUSD").as[BigDecimal] mustEqual usdVolume

    }

    "return the volume for a given trading pair for the last month" in {
      val days = 30
      val btcVolume = BigDecimal(1.09090909)
      val usdVolume = BigDecimal(12000)

      when(tradesPostgresRepository.getVolume(TradingPair.XSN_BTC, days)).thenReturn(
        TradingPairVolume(TradingPair.XSN_BTC, Satoshis.from(btcVolume).value, Satoshis.from(usdVolume).value)
      )

      val response = GET(url(TradingPair.XSN_BTC, days))
      status(response) mustEqual OK
      val json = contentAsJson(response)

      (json \ "pair").as[TradingPair] mustEqual TradingPair.XSN_BTC
      (json \ "volumeBTC").as[BigDecimal] mustEqual btcVolume
      (json \ "volumeUSD").as[BigDecimal] mustEqual usdVolume

    }

    "fail when last days is too high" in {
      val days = 993662

      val response = GET(url(TradingPair.XSN_BTC, days))
      status(response) mustEqual BAD_REQUEST
      val json = contentAsJson(response)

      (json \ "errorMessage").as[String] mustEqual "max value for lastDays is 18250"

    }

    "return a NOT_FOUND when the trading pair is invalid" in {
      val response = GET(s"/trading-pairs/LTC_WETH/volume?lastDays=1")
      status(response) mustEqual NOT_FOUND
      val json = contentAsJson(response)
      val expectedMessage = "Trading pair not found: LTC_WETH"

      (json \ "errorMessage").as[String] mustEqual expectedMessage

    }
  }

  "GET /trading-pairs/:tradingPair/tradesNumber" should {
    def url(tradingPair: TradingPair, lastDays: Int) = s"/trading-pairs/$tradingPair/trades-number?lastDays=$lastDays"
    "return the number of trades" in {
      val days = 30

      when(tradesPostgresRepository.getNumberOfTrades(TradingPair.XSN_BTC, days)).thenReturn(
        BigInt(15)
      )

      val response = GET(url(TradingPair.XSN_BTC, days))
      status(response) mustEqual OK
      val json = contentAsJson(response)

      (json \ "pair").as[TradingPair] mustEqual TradingPair.XSN_BTC
      (json \ "trades").as[BigDecimal] mustEqual 15
    }

    "return a NOT_FOUND when the trading pair is invalid" in {
      val response = GET(s"/trading-pairs/LTC_WETH/trades-number?lastDays=1")
      status(response) mustEqual NOT_FOUND
      val json = contentAsJson(response)
      val expectedMessage = "Trading pair not found: LTC_WETH"

      (json \ "errorMessage").as[String] mustEqual expectedMessage

    }

    "fail when last days is too high" in {
      val days = 993662

      val response = GET(url(TradingPair.XSN_BTC, days))
      status(response) mustEqual BAD_REQUEST

      val json = contentAsJson(response)
      (json \ "errorMessage").as[String] mustEqual "max value for lastDays is 18250"

    }
  }

  "GET /trading-pairs/:tradingPair/nodes-info" should {
    def url(tradingPair: TradingPair) = s"/trading-pairs/$tradingPair/nodes-info"

    "return the nodes info with more than one node" in {
      val client1 = randomClientPublicKey(Currency.XSN)
      val client2 = randomClientPublicKey(Currency.XSN)
      val client3 = ClientLndPublicKey(ClientPublicKeyId.random(), randomPublicKey(), Currency.BTC, client1.clientId)
      val client4 = ClientLndPublicKey(ClientPublicKeyId.random(), randomPublicKey(), Currency.BTC, client2.clientId)

      when(clientsPostgresRepository.findAllLndClientPublicKeys(currency = Currency.XSN))
        .thenReturn(List(client1, client2))
      when(clientsPostgresRepository.findAllLndClientPublicKeys(currency = Currency.BTC))
        .thenReturn(List(client3, client4))

      val openChannelsXSN = List(OpenChannel(randomOutpoint(), active = true, client1.key))
      when(multicurrencyLndDefaultImpl.getOpenChannels(Currency.XSN)).thenReturn(Future.successful(openChannelsXSN))

      val openChannelsBTC = List(
        OpenChannel(randomOutpoint(), active = true, client1.key),
        OpenChannel(randomOutpoint(), active = true, client2.key),
        OpenChannel(randomOutpoint(), active = true, client3.key),
        OpenChannel(randomOutpoint(), active = true, client4.key)
      )
      when(multicurrencyLndDefaultImpl.getOpenChannels(Currency.BTC)).thenReturn(Future.successful(openChannelsBTC))

      val response = GET(url(TradingPair.XSN_BTC))
      status(response) mustEqual OK
      val json = contentAsJson(response)

      (json \ "pair").as[TradingPair] mustEqual TradingPair.XSN_BTC
      (json \ "nodes").as[BigDecimal] mustEqual 2
      (json \ "channels").as[BigDecimal] mustEqual 5
    }

    "return the nodes info with one node" in {
      val client1 = randomClientPublicKey(Currency.XSN)
      val client3 = ClientLndPublicKey(ClientPublicKeyId.random(), randomPublicKey(), Currency.BTC, client1.clientId)

      when(clientsPostgresRepository.findAllLndClientPublicKeys(currency = Currency.XSN))
        .thenReturn(List(client1))
      when(clientsPostgresRepository.findAllLndClientPublicKeys(currency = Currency.BTC))
        .thenReturn(List(client3))

      val openChannelsXSN = List(OpenChannel(randomOutpoint(), active = true, client1.key))
      when(multicurrencyLndDefaultImpl.getOpenChannels(Currency.XSN)).thenReturn(Future.successful(openChannelsXSN))

      val openChannelsBTC = List(
        OpenChannel(randomOutpoint(), active = true, client3.key),
        OpenChannel(randomOutpoint(), active = true, client3.key),
        OpenChannel(randomOutpoint(), active = true, client3.key)
      )
      when(multicurrencyLndDefaultImpl.getOpenChannels(Currency.BTC)).thenReturn(Future.successful(openChannelsBTC))

      val response = GET(url(TradingPair.XSN_BTC))
      status(response) mustEqual OK
      val json = contentAsJson(response)

      (json \ "pair").as[TradingPair] mustEqual TradingPair.XSN_BTC
      (json \ "nodes").as[BigDecimal] mustEqual 1
      (json \ "channels").as[BigDecimal] mustEqual 4
    }

    "return the nodes info for BTC_USDT" in {
      val client1PublicKey = randomClientPublicKey(Currency.BTC)
      val client1PublicIdentifier = randomClientPublicIdentifier(Currency.USDT).copy(
        clientId = client1PublicKey.clientId
      )

      val client2PublicKey = randomClientPublicKey(Currency.BTC)
      val client2PublicIdentifier = randomClientPublicIdentifier(Currency.USDT).copy(
        clientId = client2PublicKey.clientId
      )

      when(clientsPostgresRepository.findAllLndClientPublicKeys(currency = Currency.BTC)).thenReturn(
        List(client1PublicKey, client2PublicKey)
      )
      when(clientsPostgresRepository.findAllConnextClientPublicIdentifiers(currency = Currency.USDT)).thenReturn(
        List(client1PublicIdentifier, client2PublicIdentifier)
      )

      val channelsBTC = List(OpenChannel(randomOutpoint(), active = true, client1PublicKey.key))
      when(multicurrencyLndDefaultImpl.getOpenChannels(Currency.BTC)).thenReturn(Future.successful(channelsBTC))

      val channelsUSDT = List(
        connext.Channel(Helpers.randomChannelAddress(), client1PublicIdentifier.identifier),
        connext.Channel(Helpers.randomChannelAddress(), client2PublicIdentifier.identifier),
        connext.Channel(Helpers.randomChannelAddress(), client1PublicIdentifier.identifier),
        connext.Channel(Helpers.randomChannelAddress(), client2PublicIdentifier.identifier)
      )
      when(connextHelper.getAllChannels(Currency.USDT)).thenReturn(Future.successful(channelsUSDT))

      val response = GET(url(TradingPair.BTC_USDT))
      status(response) mustEqual OK
      val json = contentAsJson(response)

      (json \ "pair").as[TradingPair] mustEqual TradingPair.BTC_USDT
      (json \ "nodes").as[BigDecimal] mustEqual 2
      (json \ "channels").as[BigDecimal] mustEqual 5
    }

    "return a NOT_FOUND when the trading pair is invalid" in {
      val response = GET(s"/trading-pairs/LTC_WETH/nodes-info")
      status(response) mustEqual NOT_FOUND
      val json = contentAsJson(response)
      val expectedMessage = "Trading pair not found: LTC_WETH"

      (json \ "errorMessage").as[String] mustEqual expectedMessage

    }
  }

  override val application: Application =
    guiceApplicationBuilder
      .overrides(bind[TradesPostgresRepository].to(tradesPostgresRepository))
      .overrides(bind[ClientsPostgresRepository].to(clientsPostgresRepository))
      .overrides(bind[MulticurrencyLndDefaultImpl].to(multicurrencyLndDefaultImpl))
      .overrides(bind[ConnextHelper].to(connextHelper))
      .build()
}
