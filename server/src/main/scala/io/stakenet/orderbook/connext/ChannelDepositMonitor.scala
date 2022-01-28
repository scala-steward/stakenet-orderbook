package io.stakenet.orderbook.connext

import akka.actor.Scheduler
import com.google.inject.Inject
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.models.ChannelIdentifier.ConnextChannelAddress
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.services.ExplorerService
import io.stakenet.orderbook.utils.RetryableFuture
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class ChannelDepositMonitor @Inject() (
    explorerService: ExplorerService,
    connextHelper: ConnextHelper,
    channelsRepository: ChannelsRepository.FutureImpl,
    retryConfig: RetryConfig
)(implicit ec: ExecutionContext, scheduler: Scheduler) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def monitor(channelAddress: ConnextChannelAddress, transactionHash: String, currency: Currency): Unit = {
    // https://ethereum.stackexchange.com/questions/319/what-number-of-confirmations-is-considered-secure-in-ethereum
    val requiredConfirmations = 12

    val waitForConfirmation = RetryableFuture.withExponentialBackoff[BigInt](
      retryConfig.initialDelay,
      retryConfig.maxDelay
    )

    val shouldWait: PartialFunction[Try[BigInt], Boolean] = {
      case Success(confirmations) if confirmations > requiredConfirmations => false
      case _ => true
    }

    val confirmations = waitForConfirmation(shouldWait) {
      for {
        transaction <- explorerService.getTransaction(currency, transactionHash).flatMap {
          case Right(transaction) => Future.successful(transaction)
          case Left(error) => Future.failed(new RuntimeException(error.getMessage))
        }

        latestBlockNumber <- explorerService.getLatestBlockNumber(currency).flatMap {
          case Right(blockNumber) => Future.successful(blockNumber)
          case Left(error) => Future.failed(new RuntimeException(error.getMessage))
        }
      } yield latestBlockNumber - transaction.blockNumber
    }

    confirmations.foreach {
      case confirmations if confirmations > requiredConfirmations =>
        val retrying = RetryableFuture.withExponentialBackoff[Unit](
          retryConfig.initialDelay,
          retryConfig.maxDelay
        )

        val shouldRetry: PartialFunction[Try[Unit], Boolean] = {
          case Success(_) => false
          case _ => true
        }

        retrying(shouldRetry) {
          for {
            _ <- connextHelper.updateChannelBalance(channelAddress, currency)
            _ <- channelsRepository.setActive(channelAddress)
          } yield ()
        }

      case _ =>
        logger.warn(s"timeout waiting for $transactionHash to confirm")
    }
  }
}
