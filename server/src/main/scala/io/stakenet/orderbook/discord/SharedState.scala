package io.stakenet.orderbook.discord

import ackcord.data.{GuildChannel, GuildId}

private[discord] class SharedState {
  import SharedState._

  private var servers: Map[GuildId, ServerDetails] = Map.empty

  /** The lock is not a problem because this is called only when the bot gets connected to the discord server.
    */
  def add(server: ServerDetails): Unit = synchronized {
    servers = servers + (server.notificationChannel.guildId -> server)
  }

  def getChannels: List[GuildChannel] = servers.map(x => x._2.notificationChannel).toList
}

object SharedState {
  case class ServerDetails(notificationChannel: GuildChannel)
}
