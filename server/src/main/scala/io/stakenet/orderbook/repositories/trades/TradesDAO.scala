package io.stakenet.orderbook.repositories.trades

import java.sql.Connection
import java.time.Instant

import anorm._
import io.stakenet.orderbook.models.{Satoshis, TradingPairPrice}
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.repositories.CommonParsers

import scala.concurrent.duration.DurationInt

private[repositories] class TradesDAO {

  def create(trade: Trade)(implicit conn: Connection): Unit = {
    val result = SQL"""
        INSERT INTO historic_trades
          (trade_id, trading_pair, price, size, existing_order_id, executing_order_id, executing_order_side, executed_on, existing_order_funds)
        VALUES (
          ${trade.id.value}::UUID,
          ${trade.pair.toString}::TRADING_PAIR,
          ${trade.price.value(Satoshis.Digits)},
          ${trade.size.value(Satoshis.Digits)},
          ${trade.existingOrder.value}::UUID,
          ${trade.executingOrder.value}::UUID,
          ${trade.executingOrderSide.toString}::ORDER_SIDE,
          ${trade.executedOn},
          ${trade.existingOrderFunds.value(Satoshis.Digits)}
        )  
        """
      .execute()

    require(!result, "Impossible, insert handled as update")
  }

  def find(id: Trade.Id)(implicit conn: Connection): Option[Trade] = {
    SQL"""
          SELECT trade_id, trading_pair, price, size, existing_order_id, executing_order_id, executing_order_side, executed_on, existing_order_funds
          FROM historic_trades
          WHERE trade_id = ${id.value}::UUID"""
      .as(TradeParsers.tradeParser.singleOpt)
  }

  def getTrades(limit: Int, tradingPair: TradingPair)(implicit conn: Connection): List[Trade] = {
    SQL"""
          SELECT trade_id, trading_pair, price, size, existing_order_id, executing_order_id, executing_order_side, executed_on, existing_order_funds
          FROM historic_trades
          WHERE trading_pair = ${tradingPair.toString}::TRADING_PAIR
          ORDER BY executed_on DESC, trade_id
          LIMIT $limit"""
      .as(TradeParsers.tradeParser.*)
  }

  def getTrades(limit: Int, after: Trade.Id, tradingPair: TradingPair)(implicit conn: Connection): List[Trade] = {
    SQL"""
          WITH CTE AS (
            SELECT executed_on AS last_seen_time
            FROM historic_trades
            WHERE trade_id = ${after.value}::UUID AND 
              trading_pair = ${tradingPair.toString}::TRADING_PAIR
          )
          SELECT trade_id, trading_pair, price, size, existing_order_id, executing_order_id, executing_order_side, executed_on, existing_order_funds
          FROM CTE CROSS JOIN historic_trades
          WHERE trading_pair = ${tradingPair.toString}::TRADING_PAIR AND (
                 executed_on < last_seen_time OR (
                   executed_on = last_seen_time AND trade_id > ${after.value}::UUID
                 )
               )
          ORDER BY executed_on DESC, trade_id
          LIMIT $limit"""
      .as(TradeParsers.tradeParser.*)
  }

  def getBars(tradingPair: TradingPair, resolution: Resolution, from: Instant, to: Instant, limit: Int)(
      implicit conn: Connection
  ): List[Bars] = {
    SQL"""
          with intervals as (
          SELECT start, start + make_interval(months =>  ${resolution.months},
									 weeks =>  ${resolution.weeks},
									 days =>  ${resolution.days}, 
									 mins => ${resolution.minutes}) 
            AS end
          FROM generate_series($from, $to, 
              make_interval(months =>  ${resolution.months},
                            weeks =>  ${resolution.weeks},
                            days =>  ${resolution.days}, 
                            mins => ${resolution.minutes})) AS start
            )
          
          SELECT DISTINCT
            intervals.start AS datetime,
            min(price) OVER w AS low,
            max(price) OVER w AS high,
            first_value(price) OVER w AS open,
            last_value(price) OVER w AS close,
            count(*) OVER w AS volume
          FROM
            intervals
          JOIN historic_trades ht ON
            trading_pair = ${tradingPair.toString}::TRADING_PAIR AND
            ht.executed_on >= intervals.start AND
            ht.executed_on < intervals.end
          window w AS (PARTITION BY intervals.start order by ht.executed_on ASC ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
          ORDER BY intervals.start
          LIMIT $limit"""
      .as(TradeParsers.barsParsers.*)
  }

  def getLastPrice(tradingPair: TradingPair)(implicit conn: Connection): Option[TradingPairPrice] = {
    SQL"""
         SELECT trading_pair, price, executed_on 
         FROM historic_trades
         WHERE trading_pair = ${tradingPair.toString}::TRADING_PAIR
         ORDER BY executed_on DESC 
         LIMIT 1"""
      .as(TradeParsers.lastPriceParser.singleOpt)
  }

  def getVolume(tradingPair: TradingPair, lastDays: Int)(implicit conn: Connection): TradingPairVolume = {
    // we use lastDays = 0 to indicate that we need all the registers
    val startDate =
      if (lastDays > 0) Instant.now().minusSeconds(lastDays.days.toSeconds)
      else Instant.ofEpochSecond(0)
    val volumeCurrency = tradingPair.secondary

    SQL"""
         WITH trades_with_rates AS (
           SELECT t.trading_pair, t.size, p.btc_price, p.usd_price
           FROM historic_trades t
           INNER JOIN LATERAL (
             SELECT btc_price, usd_price
             FROM currency_prices
             WHERE currency = ${volumeCurrency.toString}::CURRENCY_TYPE
               AND created_at <= t.executed_on
             ORDER BY created_at DESC
             LIMIT 1
           ) p ON TRUE
           WHERE t.executed_on > $startDate
             AND t.trading_pair = ${tradingPair.toString}::TRADING_PAIR
         )
         
         SELECT trading_pair, SUM(size * btc_price) AS btc_volume, SUM(size * usd_price) AS usd_volume
         FROM trades_with_rates
         GROUP BY trading_pair
         """
      .as(TradeParsers.tradingPairVolumeParser.singleOpt)
      .getOrElse(TradingPairVolume.empty(tradingPair))
  }

  def getNumberOfTrades(tradingPair: TradingPair, lastDays: Int)(implicit conn: Connection): BigInt = {
    // we use lastDays = 0 to indicate that we need all the registers
    val startDate =
      if (lastDays > 0) Instant.now().minusSeconds(lastDays.days.toSeconds)
      else Instant.ofEpochSecond(0)
    SQL"""
           SELECT COUNT(*)
           FROM historic_trades 
           WHERE trading_pair = ${tradingPair.toString}::TRADING_PAIR
              AND executed_on >= $startDate
         """
      .as(CommonParsers.bigIntParser.singleOpt)
      .getOrElse(BigInt(0))
  }
}
