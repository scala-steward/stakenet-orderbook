package io.stakenet.orderbook.actors.peers.handlers

import akka.event.LoggingAdapter
import io.stakenet.orderbook.actors.peers.handlers.CommandHandler.Result
import io.stakenet.orderbook.actors.peers.protocol.{Command, Event, HistoricDataCommand}
import io.stakenet.orderbook.repositories.trades.TradesRepository

import scala.concurrent.ExecutionContext

class HistoricDataCommandHandler(tradesRepository: TradesRepository.FutureImpl)(implicit ec: ExecutionContext)
    extends CommandHandler[HistoricDataCommand] {

  override def handle(
      cmd: HistoricDataCommand
  )(implicit ctx: CommandContext, log: LoggingAdapter): CommandHandler.Result = cmd match {
    case Command.GetHistoricTrades(limit, lastSeenTrade, tradingPair) =>
      processResponseF {
        tradesRepository
          .getTrades(limit, lastSeenTrade, tradingPair)
          .map { trades =>
            Event.CommandResponse.GetHistoricTradesResponse(trades)
          }
      }
      Result.Async

    case Command.GetBarsPrices(tradingPair, resolution, from, to, limit) =>
      processResponseF {
        tradesRepository
          .getBars(tradingPair, resolution, from, to, limit)
          .map { prices =>
            Event.CommandResponse.GetBarsPricesResponse(prices)
          }
      }
      Result.Async
  }
}
