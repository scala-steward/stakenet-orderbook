package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.services.TradingPairService

class TradingPairModule extends AbstractModule {
  override def configure(): Unit = {
    val _ = bind(classOf[TradingPairService]).to(classOf[TradingPairService.TradingPairImp])
  }
}
