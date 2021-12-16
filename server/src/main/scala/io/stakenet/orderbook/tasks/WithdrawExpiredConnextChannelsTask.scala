package io.stakenet.orderbook.tasks

import akka.actor.ActorSystem
import com.google.inject.Inject
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.utils.Extensions._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class WithdrawExpiredConnextChannelsTask @Inject() (
    channelsRepository: ChannelsRepository.FutureImpl,
    connextHelper: ConnextHelper,
    actorSystem: ActorSystem
)(implicit
    ec: ExecutionContext
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start(): Unit = {
    logger.info("Running WithdrawExpiredConnextChannels Task...")
    val initialDelay: FiniteDuration = FiniteDuration(15, "second")
    val interval: FiniteDuration = FiniteDuration(1, "hour")
    val _ = actorSystem.scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
      run()
    }
  }

  def run(): Unit = {
    channelsRepository.getConnextExpiredChannels().foreach { channels =>
      channels.foreach { channel =>
        val result = for {
          channelAddress <- channel.channelAddress
            .toRight(s"no channel address for expired channel ${channel.channelAddress}")
            .toFutureEither()

          feePayment <- channelsRepository
            .findChannelFeePayment(channel.paymentRHash, channel.payingCurrency)
            .map(_.toRight(s"no fee payment for expired channel ${channel.channelId}"))
            .toFutureEither()

          localBalance <- connextHelper
            .getChannelLocalBalance(channelAddress, feePayment.currency)
            .map(Right.apply)
            .toFutureEither()

          withdrawAmount = feePayment.capacity.max(localBalance)
          _ <- connextHelper
            .channelWithdrawal(channelAddress, withdrawAmount, feePayment.currency)
            .map(Right.apply)
            .toFutureEither()

          _ <- channelsRepository
            .setClosed(channelAddress)
            .map(Right.apply)
            .toFutureEither()
        } yield ()

        result.toFuture.onComplete {
          case Success(Right(_)) => ()
          case Success(Left(error)) => logger.warn(error)
          // This error seems to be expected when the other peer is offline so we will ignore it to avoid spamming
          // sentry with this
          case Failure(exception) if exception.getMessage.contains("Message to counterparty timed out") => ()
          case Failure(exception) => logger.error(exception.getMessage, exception)
        }
      }
    }
  }
}
