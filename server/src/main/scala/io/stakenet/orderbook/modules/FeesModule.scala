package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.impl.LndFeeService
import io.stakenet.orderbook.services.FeeService

class FeesModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[FeeService]).to(classOf[LndFeeService])
  }
}
