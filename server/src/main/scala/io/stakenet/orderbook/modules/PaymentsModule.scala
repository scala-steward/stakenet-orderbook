package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.PaymentService

class PaymentsModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[PaymentService]).to(classOf[PaymentService.LndImpl])
  }
}
