package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.IpInfoService

class IpInfoModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[IpInfoService]).to(classOf[IpInfoService.IpInfoImpl])
  }
}
