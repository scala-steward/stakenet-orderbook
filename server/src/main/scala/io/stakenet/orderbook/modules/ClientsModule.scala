package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.ClientService

class ClientsModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[ClientService]).to(classOf[ClientService.ClientServiceImpl])
  }
}
