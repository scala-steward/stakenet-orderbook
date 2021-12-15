package io.stakenet.orderbook.services

import java.time.Instant

import akka.actor.ActorSystem
import akka.testkit.TestKit
import helpers.Helpers
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{Currency, TradingPairPrice}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.apis.PriceApi
import org.mockito.Mockito
import org.mockito.MockitoSugar.{mock, verify, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration._

class CurrencyConverterSpec
    extends TestKit(ActorSystem("CurrencyConverterSpec"))
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "convert" should {
    "convert between currencies at current market price" in {
      val tradesRepository = mock[TradesRepository.Blocking]
      val converter = getCurrencyConverter(tradesRepository)
      val currency = Currency.XSN
      val targetCurrency = Currency.BTC
      val tradingPair = TradingPair.from(currency, targetCurrency)
      val lastPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)

      when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(lastPrice))

      val amount = Helpers.asSatoshis("0.00012345")
      converter.convert(amount, currency, targetCurrency).map { result =>
        result mustBe amount * lastPrice.price
      }
    }

    "convert between currencies at the default price when there is no current market price" in {
      val tradesRepository = mock[TradesRepository.Blocking]
      val converter = getCurrencyConverter(tradesRepository)
      val currency = Currency.XSN
      val targetCurrency = Currency.BTC
      val tradingPair = TradingPair.from(currency, targetCurrency)
      val defaultPrice = Helpers.asSatoshis("0.0000065")

      when(tradesRepository.getLastPrice(tradingPair)).thenReturn(None)

      val amount = Helpers.asSatoshis("0.00012345")
      converter.convert(amount, currency, targetCurrency).map { result =>
        result mustBe amount * defaultPrice
      }
    }

    "allow to convert multiple values without multiple price queries" in {
      val tradesRepository = mock[TradesRepository.Blocking]
      val converter = getCurrencyConverter(tradesRepository)
      val currency = Currency.XSN
      val targetCurrency = Currency.BTC
      val tradingPair = TradingPair.from(currency, targetCurrency)
      val lastPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)

      when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(lastPrice))

      val convert = converter.convert(currency, targetCurrency)

      val amounts = List(
        Helpers.asSatoshis("0.00012345"),
        Helpers.asSatoshis("0.00000123"),
        Helpers.asSatoshis("0.00654321")
      )

      Future.sequence(amounts.map(convert)).map { results =>
        verify(tradesRepository, Mockito.timeout(1000)).getLastPrice(tradingPair)

        results.head mustBe amounts.head * lastPrice.price
        results(1) mustBe amounts(1) * lastPrice.price
        results(2) mustBe amounts(2) * lastPrice.price
      }
    }

    "allow to convert at an specified price" in {
      val tradesRepository = mock[TradesRepository.Blocking]
      val converter = getCurrencyConverter(tradesRepository)
      val currency = Currency.XSN
      val targetCurrency = Currency.BTC
      val price = Helpers.asSatoshis("1.23456789")

      val amount = Helpers.asSatoshis("0.00012345")
      val result = converter.convert(amount, currency, targetCurrency, price)

      result mustBe amount * price
    }

    "convert back to the original amount" in {
      val tradesRepository = mock[TradesRepository.Blocking]
      val converter = getCurrencyConverter(tradesRepository)
      val currency = Currency.XSN
      val targetCurrency = Currency.BTC
      val price = Helpers.asSatoshis("1.23456789")

      val amount = Helpers.asSatoshis("0.00012345")
      val converted = converter.convert(amount, currency, targetCurrency, price)
      val result = converter.convert(converted, targetCurrency, currency, price)

      val almostEqual = (amount - result).toBigDecimal.abs < 0.0000001
      almostEqual mustBe true
    }
  }

  private def getCurrencyConverter(
      tradesRepository: TradesRepository.Blocking
  ): CurrencyConverter = {
    val tradesRepositoryAsync = new TradesRepository.FutureImpl(tradesRepository)(Executors.databaseEC)
    val retryConfig = RetryConfig(1.millisecond, 1.millisecond)
    val priceApi = new PriceApi(
      tradesRepositoryAsync,
      mock[ExplorerService],
      retryConfig
    )(Executors.globalEC, system.scheduler)

    new CurrencyConverter(priceApi)(Executors.globalEC)
  }
}
