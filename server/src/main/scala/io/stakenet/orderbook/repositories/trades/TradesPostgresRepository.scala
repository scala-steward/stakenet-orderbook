package io.stakenet.orderbook.repositories.trades

import java.time.Instant

import io.stakenet.orderbook.models.TradingPairPrice
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.repositories.trades.TradesRepository.Id
import javax.inject.Inject
import play.api.db.Database

class TradesPostgresRepository @Inject()(database: Database, dao: TradesDAO) extends TradesRepository.Blocking {

  override def create(trade: Trade): Unit = {
    database.withConnection { implicit conn =>
      dao.create(trade)
    }
  }

  def find(id: Trade.Id): Option[Trade] = {
    database.withConnection { implicit conn =>
      dao.find(id)
    }
  }

  def getTrades(limit: Int, after: Option[Trade.Id], tradingPair: TradingPair): List[Trade] = {
    database.withConnection { implicit conn =>
      after
        .map { x =>
          dao.getTrades(limit, x, tradingPair)
        }
        .getOrElse { dao.getTrades(limit, tradingPair) }
    }
  }

  override def getBars(
      tradingPair: TradingPair,
      resolution: Resolution,
      from: Instant,
      to: Instant,
      limit: Int
  ): Id[List[Bars]] = database.withConnection { implicit conn =>
    dao.getBars(tradingPair, resolution, from, to, limit)
  }

  override def getLastPrice(tradingPair: TradingPair): Id[Option[TradingPairPrice]] = database.withConnection {
    implicit conn =>
      dao.getLastPrice(tradingPair)
  }

  override def getVolume(tradingPair: TradingPair, lastDays: Int): TradingPairVolume = database.withConnection {
    implicit conn =>
      dao.getVolume(tradingPair, lastDays)
  }

  override def getNumberOfTrades(tradingPair: TradingPair, lastDays: Int): BigInt =
    database.withConnection({ implicit conn =>
      dao.getNumberOfTrades(tradingPair, lastDays)
    })
}
