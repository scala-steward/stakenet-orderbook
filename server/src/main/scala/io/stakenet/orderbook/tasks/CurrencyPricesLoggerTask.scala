package io.stakenet.orderbook.tasks

import akka.actor.ActorSystem
import com.google.inject.Inject
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.repositories.currencyPrices.CurrencyPricesRepository
import io.stakenet.orderbook.services.apis.PriceApi
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class CurrencyPricesLoggerTask @Inject() (
    currencyPricesRepository: CurrencyPricesRepository.FutureImpl,
    priceApi: PriceApi,
    actorSystem: ActorSystem
)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start(): Unit = {
    logger.info("storing currency prices...")

    val initialDelay: FiniteDuration = 1.second
    val interval: FiniteDuration = 10.minutes
    actorSystem.scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
      run()
    }

    ()
  }

  def run(): Unit = {
    Currency.forLnd.foreach { currency =>
      val result = for {
        pricesMaybe <- priceApi.getPrices(currency)
        prices = pricesMaybe.getOrElse(throw new RuntimeException(s"error getting prices for $currency"))
        result <- currencyPricesRepository.create(prices)
      } yield result

      result.onComplete {
        case Success(_) => logger.info(s"Prices stored for $currency")
        case Failure(error) => logger.info(s"Could not store prices for $currency", error)
      }
    }
  }
}
