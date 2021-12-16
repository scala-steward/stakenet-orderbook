package io.stakenet.orderbook.tasks

import java.time.Instant

import akka.actor.ActorSystem
import com.google.inject.Inject
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.lnd.channels.SubscribeChannelsObserver
import io.stakenet.orderbook.lnd.{LndHelper, MulticurrencyLndClient}
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.lnd.ChannelStatus
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.services.{ChannelService, ExplorerService}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ChannelStatusUpdaterTask @Inject() (
    channelService: ChannelService,
    lndHelper: LndHelper,
    lnd: MulticurrencyLndClient,
    discordHelper: DiscordHelper,
    actorSystem: ActorSystem,
    reportsRepository: ReportsRepository.FutureImpl,
    explorerService: ExplorerService
)(implicit
    ec: ExecutionContext
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start(): Unit = {
    Currency.forLnd.foreach { currency =>
      for {
        pendingChannels <- channelService.getProcessingChannels(currency)
        (opening, closing) = pendingChannels.partition(_.channelStatus == ChannelStatus.Opening)
        openLndChannels <- lnd.getOpenChannels(currency)
        closedOutPoints <- lnd.getClosedChannelPoints(currency)
        openOutPoints = openLndChannels.map(_.outPoint)
        openedChannels = opening.filter(x => openOutPoints.contains(x.getPoint))
        closedChannels = closing.filter(x => closedOutPoints.contains(x.getPoint))
      } yield {
        logger.info(s"Updating  ${openedChannels.length} channels already opened for ${currency.entryName}")
        openedChannels.foreach { channel =>
          val createdAt = Instant.now()
          val expiresAt = createdAt.plusSeconds(channel.lifeTimeSeconds)
          channelService
            .updateActiveChannel(channel.channelId, createdAt = createdAt, expiresAt = expiresAt)
            .onComplete {
              case Failure(e) => logger.error(s"Failed to set the channel as active, id: ${channel.channelId}", e)
              case Success(_) => ()
            }
        }
        logger.info(s"Updating  ${closedChannels.length} channels already closed for ${currency.entryName}")
        closedChannels.foreach { channel =>
          channelService.updateChannelStatus(channel.channelId, ChannelStatus.Closed).onComplete {
            case Failure(e) => logger.error(s"Failed to update the channel status, id: ${channel.channelId}", e)
            case Success(_) => ()
          }
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      def subscribe(reconnectDelay: FiniteDuration): Unit = {
        val observer = new SubscribeChannelsObserver(
          currency,
          channelService,
          discordHelper,
          lndHelper,
          reportsRepository,
          onError = () => reconnect(reconnectDelay),
          explorerService
        )

        lnd.subscribeChannelEvents(currency, observer).onComplete {
          case Failure(e) => logger.error(s"An error occurred to subscribe to $currency channel events", e)
          case Success(_) => logger.info(s"Subscribed to $currency channel events")
        }
      }

      def reconnect(delay: FiniteDuration): Unit = {
        val usedDelay = 1.minute.min(delay)
        actorSystem.scheduler.scheduleOnce(usedDelay) {
          subscribe(usedDelay * 2)
        }

        logger.info(s"Connection lost to $currency LND, trying to reconnect in $usedDelay")
      }

      subscribe(1.second)
    }
  }
}
