package io.stakenet.orderbook.repositories.trades

import anorm.{Column, Macro, RowParser}
import io.stakenet.orderbook.models.TradingPairPrice
import io.stakenet.orderbook.models.trading.{Bars, DailyPrices, OrderSide, Trade, TradingPair, TradingPairVolume}
import io.stakenet.orderbook.repositories.CommonParsers._

private[trades] object TradeParsers {

  implicit val orderSideColumn: Column[OrderSide] = enumColumn(OrderSide.withNameInsensitiveOption)
  implicit val tradingPairColumn: Column[TradingPair] = enumColumn(TradingPair.withNameInsensitiveOption)

  val tradeParser: RowParser[Trade] = Macro.parser[Trade](
    "trade_id",
    "trading_pair",
    "price",
    "size",
    "existing_order_id",
    "executing_order_id",
    "executing_order_side",
    "executed_on",
    "existing_order_funds"
  )

  val pricesParser: RowParser[DailyPrices] = Macro.parser[DailyPrices](
    "date",
    "open",
    "high",
    "low",
    "close"
  )

  val barsParsers: RowParser[Bars] = Macro.parser[Bars](
    "datetime",
    "low",
    "high",
    "open",
    "close",
    "volume"
  )

  val lastPriceParser: RowParser[TradingPairPrice] =
    Macro.parser[TradingPairPrice]("trading_pair", "price", "executed_on")

  val tradingPairVolumeParser: RowParser[TradingPairVolume] =
    Macro.parser[TradingPairVolume]("trading_pair", "btc_volume", "usd_volume")

}
