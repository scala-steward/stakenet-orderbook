package io.stakenet.orderbook.modules

import com.google.inject.AbstractModule
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.lnd.impl.MulticurrencyLndTracedImpl

class LndModule extends AbstractModule {

  override def configure(): Unit = {
    val _ = bind(classOf[MulticurrencyLndClient]).to(classOf[MulticurrencyLndTracedImpl])
  }
}
