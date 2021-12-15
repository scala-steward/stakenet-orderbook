package io.stakenet.orderbook.config

import play.api.Configuration

case class DiscordConfig(
    serverName: String,
    channelName: String,
    token: String,
    enabled: Boolean
)

object DiscordConfig {

  def apply(config: Configuration): DiscordConfig = {
    val serverName = config.get[String]("serverName")
    val channelName = config.get[String]("channelName")
    val token = config.get[String]("token")
    val enabled = config.get[Boolean]("enabled")
    DiscordConfig(serverName = serverName, channelName = channelName, token = token, enabled = enabled)
  }
}
