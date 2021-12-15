package io.stakenet.orderbook.models

import java.time.Instant

import io.stakenet.orderbook.models.trading.TradingPair

case class TradingPairPrice(tradingPair: TradingPair, price: Satoshis, executedOn: Instant)
