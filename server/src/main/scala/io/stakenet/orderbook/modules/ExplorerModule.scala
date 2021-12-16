package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.ExplorerService

class ExplorerModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[ExplorerService]).to(classOf[ExplorerService.WSImpl])
  }
}
