package io.stakenet.orderbook.repositories.trades

import java.time.Instant

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.TradingPairPrice
import io.stakenet.orderbook.models.trading._
import javax.inject.Inject

import scala.concurrent.Future

trait TradesRepository[F[_]] {
  def create(trade: Trade): F[Unit]
  def find(id: Trade.Id): F[Option[Trade]]
  def getTrades(limit: Int, after: Option[Trade.Id], tradingPair: TradingPair): F[List[Trade]]
  def getBars(tradingPair: TradingPair, resolution: Resolution, from: Instant, to: Instant, limit: Int): F[List[Bars]]
  def getLastPrice(tradingPair: TradingPair): F[Option[TradingPairPrice]]
  def getVolume(tradingPair: TradingPair, lastDays: Int): F[TradingPairVolume]
  def getNumberOfTrades(tradingPair: TradingPair, lastDays: Int): F[BigInt]
}

object TradesRepository {

  type Id[T] = T

  trait Blocking extends TradesRepository[Id]

  class FutureImpl @Inject()(blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends TradesRepository[scala.concurrent.Future] {

    override def create(trade: Trade): Future[Unit] = Future {
      blocking.create(trade)
    }

    override def find(id: Trade.Id): Future[Option[Trade]] = Future {
      blocking.find(id)
    }

    override def getTrades(limit: Int, after: Option[Trade.Id], tradingPair: TradingPair): Future[List[Trade]] =
      Future {
        blocking.getTrades(limit, after, tradingPair)
      }

    override def getBars(
        tradingPair: TradingPair,
        resolution: Resolution,
        from: Instant,
        to: Instant,
        limit: Int
    ): Future[List[Bars]] = Future {
      blocking.getBars(tradingPair, resolution, from, to, limit)
    }

    override def getLastPrice(tradingPair: TradingPair): Future[Option[TradingPairPrice]] = Future {
      blocking.getLastPrice(tradingPair)
    }

    override def getVolume(tradingPair: TradingPair, lastDays: Int): Future[TradingPairVolume] = Future {
      blocking.getVolume(tradingPair, lastDays)
    }

    override def getNumberOfTrades(tradingPair: TradingPair, lastDays: Int): Future[BigInt] = Future {
      blocking.getNumberOfTrades(tradingPair, lastDays)
    }
  }
}
