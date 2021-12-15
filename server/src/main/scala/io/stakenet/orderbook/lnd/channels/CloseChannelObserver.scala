package io.stakenet.orderbook.lnd.channels

import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.models.lnd.{ChannelStatus, LndChannel}
import io.stakenet.orderbook.services.ChannelService
import lnrpc.rpc.CloseStatusUpdate
import lnrpc.rpc.CloseStatusUpdate.Update
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class CloseChannelObserver(expiredChannel: LndChannel, channelService: ChannelService)(
    implicit ec: ExecutionContext
) extends StreamObserver[CloseStatusUpdate] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def onNext(closeStatusUpdate: CloseStatusUpdate): Unit = {
    closeStatusUpdate.update match {
      case Update.Empty =>
      case Update.ClosePending(_) => {
        logger.info(
          s"Closing Channel = ${expiredChannel.channelId}, currency = ${expiredChannel.currency.entryName}, txid = ${expiredChannel.fundingTransaction}"
        )
        val _ = channelService.updateChannelStatus(expiredChannel.channelId, ChannelStatus.Closing)
      }
      case Update.ChanClose(_) => {
        logger.info(
          s"Channel closed = ${expiredChannel.channelId}, currency = ${expiredChannel.currency}, txid = ${expiredChannel.fundingTransaction.toString}"
        )
        val _ = channelService.updateChannelStatus(expiredChannel.channelId, ChannelStatus.Closed).onComplete {
          case Failure(e) => logger.error(s"Failed to update the channel status, id: ${expiredChannel.channelId}", e)
          case Success(_) => ()
        }
      }
    }
  }

  override def onError(t: Throwable): Unit = {
    // TODO: what to do if an error occurred while closing channel?
    logger.error(s"An error occurred to close the channel with txid = ${expiredChannel.fundingTransaction}", t)
    val _ = channelService.updateChannelStatus(expiredChannel.channelId, ChannelStatus.Active)
  }

  override def onCompleted(): Unit = logger.info(s"Completed with channel id = ${expiredChannel.channelId}")
}
