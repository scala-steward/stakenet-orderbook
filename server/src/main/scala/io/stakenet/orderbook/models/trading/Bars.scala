package io.stakenet.orderbook.models.trading

import java.time.Instant

import io.stakenet.orderbook.models.Satoshis

case class Bars(time: Instant, low: Satoshis, high: Satoshis, open: Satoshis, close: Satoshis, volume: Long) {}
