package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.tasks._

class TasksModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[UnlinkAllFeesTask]).asEagerSingleton()
    bind(classOf[CloseExpiredChannelsTask]).asEagerSingleton()
    bind(classOf[WithdrawExpiredConnextChannelsTask]).asEagerSingleton()
    bind(classOf[ChannelStatusUpdaterTask]).asEagerSingleton()
    bind(classOf[ConnextChannelStatusUpdater]).asEagerSingleton()
    bind(classOf[ClientStatusLoggerTask]).asEagerSingleton()
    bind(classOf[CurrencyPricesLoggerTask]).asEagerSingleton()
  }
}
