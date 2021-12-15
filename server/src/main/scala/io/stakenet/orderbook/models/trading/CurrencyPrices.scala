package io.stakenet.orderbook.models.trading

import java.time.Instant

import io.stakenet.orderbook.models.Currency

case class CurrencyPrices(currency: Currency, btcPrice: BigDecimal, usdPrice: BigDecimal, createdAt: Instant)
