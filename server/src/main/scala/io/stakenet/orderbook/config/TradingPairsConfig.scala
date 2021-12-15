package io.stakenet.orderbook.config

import io.stakenet.orderbook.models.trading.TradingPair
import play.api.Configuration

case class TradingPairsConfig(
    enabled: Set[TradingPair]
)

object TradingPairsConfig {

  def apply(config: Configuration): TradingPairsConfig = {
    val enabled = TradingPair.values.filter { pair =>
      config.get[Configuration](pair.entryName).get[Boolean]("enabled")
    }
    new TradingPairsConfig(enabled.toSet)
  }
}
