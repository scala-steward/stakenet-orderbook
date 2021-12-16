package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.ChannelService

class ChannelsModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[ChannelService]).to(classOf[ChannelService.ChannelImp])
  }
}
