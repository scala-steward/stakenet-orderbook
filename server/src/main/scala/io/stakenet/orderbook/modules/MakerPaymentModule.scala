package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.MakerPaymentService

class MakerPaymentModule extends AbstractModule {
  override def configure(): Unit = {
    val _ = bind(classOf[MakerPaymentService]).to(classOf[MakerPaymentService.Impl])
  }
}
