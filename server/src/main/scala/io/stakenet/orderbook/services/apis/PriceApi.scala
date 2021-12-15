package io.stakenet.orderbook.services.apis

import akka.actor.Scheduler
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.models.trading.{CurrencyPrices, TradingPair}
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.ExplorerService
import io.stakenet.orderbook.services.ExplorerService.ExplorerErrors
import io.stakenet.orderbook.utils.RetryableFuture
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PriceApi @Inject()(
    tradesRepository: TradesRepository.FutureImpl,
    explorerService: ExplorerService,
    retryConfig: RetryConfig
)(
    implicit ec: ExecutionContext,
    scheduler: Scheduler
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def getCurrentPrice(tradingPair: TradingPair): Future[Satoshis] = {
    tradesRepository.getLastPrice(tradingPair).map {
      case Some(lastPrice) => lastPrice.price
      case None => getDefaultPrice(tradingPair)
    }
  }

  // TODO: remove hardcoded prices, add a call to the explorer api.
  private def getDefaultPrice(tradingPair: TradingPair): Satoshis = tradingPair match {
    case TradingPair.XSN_BTC =>
      Satoshis.from(BigDecimal("0.0000065")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.XSN_LTC =>
      Satoshis.from(BigDecimal("0.00103524")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.LTC_BTC =>
      Satoshis.from(BigDecimal("0.00597465")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.XSN_WETH =>
      Satoshis.from(BigDecimal("0.0001033")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.BTC_WETH =>
      Satoshis.from(BigDecimal("15.32")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.ETH_BTC =>
      Satoshis.from(BigDecimal("0.07")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.BTC_USDC =>
      Satoshis.from(BigDecimal("38000.0")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.ETH_USDC =>
      Satoshis.from(BigDecimal("2600.0")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.BTC_USDT =>
      Satoshis.from(BigDecimal("38000.0")).getOrElse(throw new RuntimeException("invalid satoshis"))
    case TradingPair.XSN_ETH =>
      Satoshis.from(BigDecimal("0.0001033")).getOrElse(throw new RuntimeException("invalid satoshis"))
  }

  def getUSDPrice(currency: Currency): Future[Option[BigDecimal]] = {
    // USDT/USDC price is always ~1 USD so no need to request the price from the explorer
    if (currency == Currency.USDT || currency == Currency.USDC) {
      Future.successful(Some(1.0))
    } else {
      val retrying = RetryableFuture.withExponentialBackoff[Either[ExplorerErrors, BigDecimal]](
        retryConfig.initialDelay,
        retryConfig.maxDelay
      )

      val shouldRetry: Try[Either[ExplorerErrors, BigDecimal]] => Boolean = {
        case Success(Left(_)) => true
        case Failure(_) => true
        case _ => false
      }

      retrying(shouldRetry) {
        explorerService.getUSDPrice(currency)
      }.map {
        case Left(value) =>
          logger.warn(s"${value.getMessage} for ${currency.entryName}")
          None
        case Right(value) => Some(value)
      }
    }
  }

  def getPrices(currency: Currency): Future[Option[CurrencyPrices]] = {
    val retrying = RetryableFuture.withExponentialBackoff[Either[ExplorerErrors, CurrencyPrices]](
      retryConfig.initialDelay,
      retryConfig.maxDelay
    )

    val shouldRetry: Try[Either[ExplorerErrors, CurrencyPrices]] => Boolean = {
      case Success(Left(_)) => true
      case Failure(_) => true
      case _ => false
    }

    retrying(shouldRetry) {
      explorerService.getPrices(currency)
    }.map {
      case Left(value) =>
        logger.warn(s"${value.getMessage} for ${currency.entryName}")
        None
      case Right(value) => Some(value)
    }
  }
}
