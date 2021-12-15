package io.stakenet.orderbook.models.trading

case class TradingPairDailyPrices(tradingPair: TradingPair, prices: List[DailyPrices])
