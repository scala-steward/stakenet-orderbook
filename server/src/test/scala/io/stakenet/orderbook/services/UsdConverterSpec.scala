package io.stakenet.orderbook.services

import akka.actor.ActorSystem
import akka.testkit.TestKit
import helpers.Helpers
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.ExplorerService.ExplorerErrors.InvalidJsonData
import io.stakenet.orderbook.services.UsdConverter.RateUnavailable
import io.stakenet.orderbook.services.apis.PriceApi
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration._

class UsdConverterSpec
    extends TestKit(ActorSystem("CurrencyConverterSpec"))
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "convert" should {
    "convert Satoshis to USD" in {
      val explorerService = mock[ExplorerService]
      val converter = getCurrencyConverter(explorerService)
      val currency = Currency.XSN
      val usdPrice = BigDecimal(123)

      when(explorerService.getUSDPrice(currency)).thenReturn(Future.successful(Right(usdPrice)))

      val amount = Helpers.asSatoshis("0.00012345")
      converter.convert(amount, currency).map { result =>
        result mustBe Right(amount.toBigDecimal * usdPrice)
      }
    }

    "fail to convert Satoshis to USD when usd rate is not available" in {
      val explorerService = mock[ExplorerService]
      val converter = getCurrencyConverter(explorerService)
      val currency = Currency.XSN

      when(explorerService.getUSDPrice(currency)).thenReturn(Future.successful(Left(InvalidJsonData(""))))

      val amount = Helpers.asSatoshis("0.00012345")
      converter.convert(amount, currency).map { result =>
        result mustBe Left(RateUnavailable(currency))
      }
    }

    "convert USD to Satoshis" in {
      val explorerService = mock[ExplorerService]
      val converter = getCurrencyConverter(explorerService)
      val currency = Currency.XSN
      val usdPrice = BigDecimal(123)

      when(explorerService.getUSDPrice(currency)).thenReturn(Future.successful(Right(usdPrice)))

      val amount = BigDecimal(123)
      converter.convert(amount, currency).map { result =>
        result mustBe Right(Satoshis.from(amount / usdPrice).value)
      }
    }

    "fail to convert USD to Satoshis when usd rate is unavailable" in {
      val explorerService = mock[ExplorerService]
      val converter = getCurrencyConverter(explorerService)
      val currency = Currency.XSN

      when(explorerService.getUSDPrice(currency)).thenReturn(Future.successful(Left(InvalidJsonData(""))))

      val amount = BigDecimal(123)
      converter.convert(amount, currency).map { result =>
        result mustBe Left(RateUnavailable(currency))
      }
    }
  }

  private def getCurrencyConverter(explorerService: ExplorerService): UsdConverter = {
    val retryConfig = RetryConfig(1.millisecond, 1.millisecond)
    val priceApi = new PriceApi(
      mock[TradesRepository.FutureImpl],
      explorerService,
      retryConfig
    )(Executors.globalEC, system.scheduler)

    new UsdConverter(priceApi)(Executors.globalEC)
  }
}
