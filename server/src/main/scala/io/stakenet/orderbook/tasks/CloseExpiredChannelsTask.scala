package io.stakenet.orderbook.tasks

import akka.actor.ActorSystem
import com.google.inject.Inject
import io.stakenet.orderbook.lnd.channels.CloseChannelObserver
import io.stakenet.orderbook.lnd.{LndHelper, MulticurrencyLndClient}
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.explorer.EstimatedFee
import io.stakenet.orderbook.models.lnd.{ChannelStatus, LndChannel}
import io.stakenet.orderbook.services.{ChannelService, ExplorerService}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class CloseExpiredChannelsTask @Inject() (
    channelService: ChannelService,
    lndHelper: LndHelper,
    lnd: MulticurrencyLndClient,
    actorSystem: ActorSystem,
    explorerService: ExplorerService
)(implicit
    ec: ExecutionContext
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start(): Unit = {
    logger.info("Running Channels Task...")
    val initialDelay: FiniteDuration = FiniteDuration(15, "second")
    val interval: FiniteDuration = FiniteDuration(1, "hour")
    val _ = actorSystem.scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
      run()
    }
  }

  def run(): Unit = {
    Currency.forLnd.foreach { currency =>
      for {
        estimatedFee <- explorerService.getEstimateFee(currency)
        estimatedFeePerByte = estimatedFee match {
          case Left(_) => EstimatedFee(currency.networkFee).perByte
          case Right(estimatedFee) => estimatedFee.perByte
        }
        expiredChannels <- channelService.getExpiredChannels(currency)
        openLndChannels <- lnd.getOpenChannels(currency)
        closedOutPoints <- lnd.getClosedChannelPoints(currency)
        waitingToCloseOutPoints <- lnd.getPendingChannels(currency)
        toUpdate = expiredChannels.filter(expired => closedOutPoints.contains(expired.getPoint))
        toClose = expiredChannels.flatMap { expiredChannel =>
          openLndChannels
            .find(_.outPoint == expiredChannel.getPoint)
            .map(x => (expiredChannel, x.active))
        }
        waitingToClose = expiredChannels.filter(expired => waitingToCloseOutPoints.contains(expired.getPoint))
      } yield {
        logger.info(s"Closing ${toClose.length} channels for ${currency.entryName}")
        toClose.foreach(x => closeExpiredChannel(x._1, x._2, estimatedFeePerByte))
        logger.info(s"Updating  ${toUpdate.length} channels already closed for ${currency.entryName}")
        toUpdate.foreach(x => updateChannelStatus(x, ChannelStatus.Closed))
        logger.info(s"Updating  ${toUpdate.length} channels waiting to close for ${currency.entryName}")
        waitingToClose.foreach(x => updateChannelStatus(x, ChannelStatus.Closing))
      }
    }
  }

  private def closeExpiredChannel(expiredChannel: LndChannel, active: Boolean, estimatedFeePerByte: Satoshis): Unit = {
    val closeChannelObserver = new CloseChannelObserver(expiredChannel, channelService)

    updateChannelStatus(expiredChannel, ChannelStatus.Closing)
    lndHelper.closeChannel(expiredChannel, closeChannelObserver, active, estimatedFeePerByte).onComplete {
      case Failure(e) =>
        logger.error(
          s"Failed to close the channel, id: ${expiredChannel.channelId}, funding txid: ${expiredChannel.fundingTransaction}",
          e
        )
      case Success(_) =>
        channelService.createCloseExpiredChannelRequest(expiredChannel.channelId, active).onComplete {
          case Failure(e) => logger.error(s"Failed to create close channel request, id: ${expiredChannel.channelId}", e)
          case Success(_) => ()
        }
    }
  }

  private def updateChannelStatus(expiredChannel: LndChannel, channelStatus: ChannelStatus): Unit = {
    channelService.updateChannelStatus(expiredChannel.channelId, channelStatus).recover { case e =>
      logger.error(
        s"Failed to update the channel status to ${channelStatus.entryName}, id: ${expiredChannel.channelId}",
        e
      )
    }
    ()
  }
}
