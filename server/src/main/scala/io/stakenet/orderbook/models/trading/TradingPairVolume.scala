package io.stakenet.orderbook.models.trading

import io.stakenet.orderbook.models.Satoshis

case class TradingPairVolume(tradingPair: TradingPair, btcVolume: Satoshis, usdVolume: Satoshis)

object TradingPairVolume {
  def empty(tradingPair: TradingPair): TradingPairVolume = TradingPairVolume(tradingPair, Satoshis.Zero, Satoshis.Zero)
}
