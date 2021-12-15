package io.stakenet.orderbook.discord

import ackcord.data.{GuildChannel, GuildId, TextChannelId}
import ackcord.requests.{CreateMessage, CreateMessageData, GetCurrentUserGuildsData, GetUserGuildsGuild}
import ackcord.{CacheSnapshot, DiscordClient}
import io.stakenet.orderbook.config.DiscordConfig
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A high-level API to interact with discord based on our config
 */
class DiscordAPI(discordConfig: DiscordConfig, discordClient: DiscordClient)(
    implicit c: CacheSnapshot,
    ec: ExecutionContext
) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Get the guilds where the bot is installed, and where the app is configured to use.
   */
  def getSupportedGuilds: Future[Seq[GetUserGuildsGuild]] = {
    getGuilds()
      .map { guilds =>
        guilds.filter(discordConfig.serverName == _.name)
      }
  }

  /**
   * Gets the guild channel that the bot uses to send notifications.
   */
  def getNotificationChannel(guildId: GuildId): Future[Option[GuildChannel]] = {
    getChannels(guildId)
      .map { channels =>
        channels.find(_.name.contains(discordConfig.channelName))
      }
  }

  /**
   * Send a message to the given channel, in case of failure, just log a warning.
   */
  def sendMessage(channel: GuildChannel, msg: String): Unit = {
    // TODO: Extract to another class, and write tests for it
    def keepUntil(text: String, maxSize: Int): List[String] = {
      val (pieces, last) = text
        .split("\n")
        .foldLeft((List.empty[String], "")) {
          case ((previous, acc), cur) =>
            if (acc.length + cur.length + 1 < maxSize) {
              (previous, acc + "\n" + cur)
            } else {
              (acc :: previous, cur)
            }
        }
      (last :: pieces).reverse
    }

    def doSend(textChannelId: TextChannelId, text: String): Future[Unit] = {
      for {
        request <- Future.fromTry {
          Try { CreateMessage(textChannelId, CreateMessageData(content = text)) }
        }
        _ <- discordClient.requestsHelper.run(request).value
      } yield ()
    }

    val textChannelId = TextChannelId(channel.id.toString)
    val result = keepUntil(msg, 2000).foldLeft(Future.unit) {
      case (acc, cur) =>
        for {
          _ <- acc
          _ <- doSend(textChannelId, cur)
        } yield ()
    }

    result.onComplete {
      case Success(_) => ()
      case Failure(ex) =>
        logger.warn(s"Failed to send message, guild = ${channel.guildId}, channel = ${channel.name}, msg = $msg", ex)
    }
  }

  private def getGuilds(): Future[Seq[GetUserGuildsGuild]] = {
    val request = ackcord.requests.GetCurrentUserGuilds(GetCurrentUserGuildsData())
    discordClient.requestsHelper
      .run(request)
      .value
      .map(_.getOrElse(List.empty))
  }

  private def getChannels(guildId: GuildId): Future[Seq[GuildChannel]] = {
    val request = ackcord.requests.GetGuildChannels(guildId)
    discordClient.requestsHelper
      .run(request)
      .value
      .map(_.getOrElse(List.empty))
      .map(_.flatMap(_.toGuildChannel(guildId)))
  }
}
