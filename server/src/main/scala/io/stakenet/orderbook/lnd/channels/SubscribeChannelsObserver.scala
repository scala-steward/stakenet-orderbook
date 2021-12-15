package io.stakenet.orderbook.lnd.channels

import java.time.Instant

import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.lnd.LndHelper
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.{Channel, Currency}
import io.stakenet.orderbook.models.lnd.LndTxid
import io.stakenet.orderbook.models.reports.ChannelRentalFee
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.services.{ChannelService, ExplorerService}
import lnrpc.rpc.{ChannelCloseSummary, ChannelEventUpdate}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SubscribeChannelsObserver(
    currency: Currency,
    channelService: ChannelService,
    discordHelper: DiscordHelper,
    lndHelper: LndHelper,
    reportsRepository: ReportsRepository.FutureImpl,
    onError: () => Unit,
    explorerService: ExplorerService
)(
    implicit ec: ExecutionContext
) extends StreamObserver[ChannelEventUpdate] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def onNext(value: ChannelEventUpdate): Unit = value.channel match {
    case ChannelEventUpdate.Channel.Empty => logger.info(s"An empty value received from ${currency.entryName} lnd")
    case ChannelEventUpdate.Channel.OpenChannel(channel) => {
      val outpoint = LndOutpoint
        .untrusted(channel.channelPoint)
        .getOrElse(throw new RuntimeException("An invalid outpoint was received from lnd"))

      val nodePublicKey = channel.remotePubkey
      channelService
        .findChannelFeePayment(outpoint)
        .flatMap {
          case Some(fee) =>
            val createdAt = Instant.now()
            val expiresAt = createdAt.plusSeconds(fee.lifeTimeSeconds)

            channelService.updateActiveChannel(outpoint, createdAt, expiresAt)
          case None =>
            // This was probably not a rented channel
            Future.unit
        }
        .onComplete {
          case Success(_) =>
            logger.info(s"Channel opened to $nodePublicKey, txid = ${outpoint.txid}")

          case Failure(exception) =>
            logger.error(
              s"Channel opened on lnd but failed to set it on the database, to = $nodePublicKey, outpoint = $outpoint",
              exception
            )
        }
    }
    case ChannelEventUpdate.Channel.ClosedChannel(channel) =>
      val outpoint = LndOutpoint
        .untrusted(channel.channelPoint)
        .getOrElse(throw new RuntimeException("An invalid outpoint was received from lnd"))

      channelService.findChannel(outpoint).onComplete {
        case Success(Some(rentedChannel)) =>
          if (!rentedChannel.isExpired) {
            notifyUnexpiredChannelClosed(rentedChannel, channel.closeInitiator.name, channel.closeType.name)
          }

          logChannelFees(rentedChannel, outpoint, channel.closingTxHash)

          updateClosedChannel(outpoint, channel)
        case Failure(error) =>
          logger.error(s"Could not fetch channel with outpoint $outpoint", error)
        case _ =>
          // The closed channel was not rented on the orderbook, we do nothing
          ()
      }
    case ChannelEventUpdate.Channel.ActiveChannel(point) =>
      logger.info(s"Active channel: ${point.getFundingTxidStr} on ${currency.entryName}")
    case ChannelEventUpdate.Channel.InactiveChannel(point) =>
      logger.info(s"Inactive channel: ${point.getFundingTxidStr} on ${currency.enumEntry}")
    case ChannelEventUpdate.Channel.PendingOpenChannel(_) => ()
  }

  override def onError(t: Throwable): Unit = {
    logger.error(s"An error occurred on channel events for ${currency.entryName} ", t)

    onError()
  }

  override def onCompleted(): Unit = logger.info(s"Completed channel stream on ${currency.entryName}")

  private def notifyUnexpiredChannelClosed(
      channel: Channel.LndChannel,
      closeInitiator: String,
      closeType: String
  ) = {
    val msj =
      "Not expired channel closed, channelId: %s, expirationDate: %s, currentDate: %s, remainingTime: %s, closedBy: %s, closeType: %s"
        .format(
          channel.channelId,
          channel.expiresAt,
          Instant.now,
          channel.remainingTime,
          closeInitiator,
          closeType
        )

    logger.warn(msj)
    discordHelper.sendMessage(msj)
  }

  private def updateClosedChannel(outpoint: LndOutpoint, channel: ChannelCloseSummary) = {
    channelService.updateClosedChannel(outpoint, channel.closeType.name, channel.closeInitiator.name).onComplete {
      case Success(_) =>
        val message = "Channel closed to %s, txid = %s, reason = %s, closedBy = %s".format(
          channel.remotePubkey,
          outpoint.txid,
          channel.closeType.name,
          channel.closeInitiator.name
        )

        logger.info(message)
        discordHelper.sendMessage(message)
      case Failure(exception) =>
        val nodePublicKey = channel.remotePubkey
        logger.error(
          s"Channel closed on lnd but failed to set it on the database, to = $nodePublicKey, outpoint = $outpoint",
          exception
        )
    }
  }

  def logChannelFees(channel: Channel.LndChannel, outpoint: LndOutpoint, closingTxHash: String) = {
    val fees = for {
      feePayment <- channelService.findChannelFeePayment(channel.channelId)
      openingFee <- lndHelper.getTransactionFee(outpoint.txid.toString, currency)
      closingFee <- explorerService.getTransactionFee(currency, closingTxHash)
    } yield (feePayment, openingFee, closingFee)

    fees.onComplete {
      case Success((Some(feePayment), Some(openingFee), Right(closingFee))) =>
        (channel.fundingTransaction, LndTxid.untrusted(closingTxHash), channel.createdAt) match {
          case (Some(fundingTransaction), Some(closingTransaction), Some(createdAt)) =>
            val channelRentalFee = ChannelRentalFee(
              channel.paymentRHash,
              feePayment.payingCurrency,
              feePayment.currency,
              feePayment.paidFee,
              feePayment.capacity,
              fundingTransaction,
              openingFee,
              closingTransaction,
              closingFee,
              createdAt,
              lifeTimeSeconds = feePayment.lifeTimeSeconds
            )

            reportsRepository.createChannelRentalFee(channelRentalFee).onComplete {
              case Success(_) => logger.info("channel fees logged successfully")
              case Failure(e) => logger.error("error logging channel fees", e)
            }
          case (None, _, _) =>
            logger.info(s"Invalid state for channel ${channel.channelId}, closed channel without funding transaction")
          case (_, None, _) =>
            logger.info(s"Could not parse closing transaction for ${channel.channelId}")
          case (_, _, None) =>
            logger.info(s"Invalid state for channel ${channel.channelId}, closed channel without opening date")
        }
      case Success((None, _, _)) =>
        logger.info(s"channel fee for ${channel.channelId} not found")
      case Success((_, None, _)) =>
        logger.info(s"Opening fee for ${channel.channelId} not found")
      case Success((_, _, Left(_))) =>
        logger.info(s"Closing fee for ${channel.channelId} not found")
      case Failure(e) =>
        logger.error(e.getMessage, e)
    }
  }
}
