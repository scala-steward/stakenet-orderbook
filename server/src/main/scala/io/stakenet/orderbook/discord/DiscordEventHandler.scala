package io.stakenet.orderbook.discord

import ackcord.APIMessage
import ackcord.data.GuildId
import io.stakenet.orderbook.discord.commands.ExternalCommandHandler
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Processes the events produced by discord, we are particularly interested on:
 * - Ready
 * - Guild Create
 */
private[discord] class DiscordEventHandler(
    discordAPI: DiscordAPI,
    sharedState: SharedState,
    externalCommandHandler: ExternalCommandHandler
)(implicit
    ec: ExecutionContext
) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def handler(): PartialFunction[APIMessage, Unit] = {
    case _: APIMessage.Ready => initialize()
    case e: APIMessage.GuildCreate => logger.info(s"Bot installed on ${e.guild.name}")
    case msg: APIMessage.MessageCreate =>
      sharedState.getChannels
        .find(_.id.toString == msg.message.channelId.toString)
        .foreach { channel =>
          externalCommandHandler.handlePossibleCommand(channel, msg)
        }

    case _ => ()
  }

  // When the client is ready, initialize the shared state, so that we sync the config with discord.
  private def initialize(): Unit = {
    logger.info("Client ready, initializing")

    def getDetails(guildId: GuildId) = {
      discordAPI
        .getNotificationChannel(guildId)
        .map {
          case None => throw new RuntimeException(s"Missing notification channel for guild = $guildId")
          case Some(channel) => SharedState.ServerDetails(channel)
        }
    }

    val result = for {
      guilds <- discordAPI.getSupportedGuilds
      guildsChannelF = guilds.map { case (guild) =>
        getDetails(guild.id)
      }
      guildChannels <- Future.sequence(guildsChannelF)
    } yield {
      guildChannels.foreach { channel =>
        sharedState.add(channel)
      }
    }

    result.onComplete {
      case Success(_) => ()
      case Failure(ex) =>
        logger.warn(s"Failed to initialize", ex)
    }
  }
}
