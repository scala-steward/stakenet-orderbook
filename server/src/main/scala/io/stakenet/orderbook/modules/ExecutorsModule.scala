package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.executors.{DatabaseAkkaExecutionContext, DatabaseExecutionContext}

class ExecutorsModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[DatabaseExecutionContext]).to(classOf[DatabaseAkkaExecutionContext]).asEagerSingleton()
  }
}
