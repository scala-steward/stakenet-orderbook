package io.stakenet.orderbook.services

import io.stakenet.orderbook.models.trading.{TradingPair, TradingPairVolume}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import javax.inject.Inject

import scala.concurrent.Future

trait TradingPairService {
  // return the volume for the given last days
  def getVolume(tradingPair: TradingPair, lastDays: Int): Future[TradingPairVolume]
  def getNumberOfTrades(tradingPair: TradingPair, lastDays: Int): Future[BigInt]
}

object TradingPairService {

  class TradingPairImp @Inject() (tradesRepository: TradesRepository.FutureImpl) extends TradingPairService {

    override def getVolume(tradingPair: TradingPair, lastDays: Int): Future[TradingPairVolume] = {
      tradesRepository.getVolume(tradingPair, lastDays)
    }

    override def getNumberOfTrades(tradingPair: TradingPair, lastDays: Int): Future[BigInt] = {
      tradesRepository.getNumberOfTrades(tradingPair, lastDays)
    }
  }
}
