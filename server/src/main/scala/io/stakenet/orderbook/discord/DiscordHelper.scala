package io.stakenet.orderbook.discord

import ackcord.ClientSettings
import ackcord.data.GuildChannel
import ackcord.gateway.GatewayIntents
import akka.actor._
import com.google.inject.Inject
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.config.DiscordConfig
import io.stakenet.orderbook.discord.commands.ExternalCommandHandler
import io.stakenet.orderbook.discord.commands.clientsStatus.ClientsStatusCommandHandler
import io.stakenet.orderbook.discord.commands.maintenance.MaintenanceCommandHandler
import io.stakenet.orderbook.discord.commands.report.ReportCommandHandler
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.services.apis.PriceApi
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

class DiscordHelper @Inject()(
    discordConfig: DiscordConfig,
    actorSystem: ActorSystem,
    reportsRepository: ReportsRepository.FutureImpl,
    priceApi: PriceApi,
    messageFilter: PeerMessageFilterActor.Ref
) {

  private val clientSettings = ClientSettings(
    token = discordConfig.token,
    system = typed.ActorSystem.wrap(actorSystem),
    intents = GatewayIntents.Guilds ++ GatewayIntents.GuildMessages
  )
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val sharedState = new SharedState
  private var discordAPI: Option[DiscordAPI] = None

  if (discordConfig.enabled) {
    run()
  } else {
    logger.info(s"The Discord module is disabled")
  }

  private def run(): Unit = {

    import clientSettings.executionContext

    logger.info("Starting client")
    clientSettings.createClient().onComplete {
      case Success(client) =>
        client.onEventSideEffects { implicit c =>
          val discordApi = new DiscordAPI(discordConfig, client)
          val reportCommandHandler = new ReportCommandHandler(reportsRepository, priceApi, discordApi)
          val maintenanceCommandHandler = new MaintenanceCommandHandler(messageFilter, discordApi)
          val clientsStatusCommandHandler = new ClientsStatusCommandHandler(reportsRepository, discordApi)

          val externalCommandHandler = new ExternalCommandHandler(
            reportCommandHandler,
            maintenanceCommandHandler,
            clientsStatusCommandHandler,
            discordApi
          )
          val eventHandler = new DiscordEventHandler(discordApi, sharedState, externalCommandHandler)
          discordAPI = Some(discordApi)
          eventHandler.handler()
        }
        client.login()

      case Failure(ex) =>
        logger.error("Failed to create the discord client...", ex)
    }
  }

  def sendMessage(message: String): Unit = {
    sharedState.getChannels.foreach(channel => sendMessage(channel, message))
  }

  def sendMessage(channel: GuildChannel, message: String): Unit = {
    if (discordConfig.enabled) {
      discordAPI match {
        case Some(api) => api.sendMessage(channel, message)
        case None => logger.warn("The discord api is not ready")
      }
    }
  }
}
