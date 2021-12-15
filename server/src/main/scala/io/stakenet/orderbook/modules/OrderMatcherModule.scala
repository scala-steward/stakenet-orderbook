package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.OrderMatcherService
import io.stakenet.orderbook.services.impl.TreeBasedOrderMatcherService

class OrderMatcherModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[OrderMatcherService]).to(classOf[TreeBasedOrderMatcherService])
  }
}
