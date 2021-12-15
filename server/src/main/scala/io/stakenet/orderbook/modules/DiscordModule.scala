package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.discord.DiscordHelper

class DiscordModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[DiscordHelper]).asEagerSingleton()
  }
}
