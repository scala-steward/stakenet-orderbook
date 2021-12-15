package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.{ETHService, ETHServiceRPCImpl}

class ETHServiceModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[ETHService]).to(classOf[ETHServiceRPCImpl])
  }
}
