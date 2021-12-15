package io.stakenet.orderbook.actors.peers.handlers

import akka.event.LoggingAdapter
import akka.pattern.ask
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers.PeerActor.InternalMessage
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CommandFailed
import io.stakenet.orderbook.actors.peers.{PeerActorOps, PeerUser}
import io.stakenet.orderbook.actors.peers.protocol.{Command, Event, UncategorizedCommand}
import io.stakenet.orderbook.config.TradingPairsConfig

import scala.concurrent.{ExecutionContext, Future}

class UncategorizedCommandHandler(
    orderManager: OrderManagerActor.Ref,
    peerActorOps: PeerActorOps,
    tradingPairsConfig: TradingPairsConfig
)(
    implicit ec: ExecutionContext
) extends CommandHandler[UncategorizedCommand] {
  override def handle(
      cmd: UncategorizedCommand
  )(implicit ctx: CommandContext, log: LoggingAdapter): CommandHandler.Result = cmd match {
    case Command.InvalidCommand(reason) =>
      processResponse {
        Event.CommandResponse.CommandFailed(reason)
      }
      CommandHandler.Result.Async
    case Command.Ping() =>
      processResponse {
        Event.CommandResponse.PingResponse()
      }
      CommandHandler.Result.Async

    case Command.GetTradingPairs() =>
      val pairs = tradingPairsConfig.enabled.toList
      val paysFees = peerActorOps.getPaysFees

      processResponse {
        Event.CommandResponse.GetTradingPairsResponse(pairs, paysFees)
      }
      CommandHandler.Result.Async

    case Command.CleanTradingPairOrders(tradingPair) =>
      val matchedOrdersRemove = ctx.state.matched.filter(_.trade.pair == tradingPair)

      processResponseF {
        (orderManager.ref ? OrderManagerActor.Command.CleanOpenOrders(tradingPair, ctx.self))
          .mapTo[OrderManagerActor.Event.OpenOrdersCleaned]
          .map { x =>
            ctx.self ! InternalMessage.CleanOrders(tradingPair)

            matchedOrdersRemove.foreach { peerTrade =>
              peerActorOps.tryCancelingPayment(peerTrade.trade.executingOrder)(log)(ctx.peerUser)
              peerActorOps.tryCancelingPayment(peerTrade.trade.existingOrder)(log)(ctx.peerUser)
              peerTrade.secondOrder.peer ! Event.ServerEvent.MyMatchedOrderCanceled(peerTrade.trade)

            }

            Event.CommandResponse.CleanTradingPairOrdersResponse(
              x.tradingPair,
              x.ordersRemoved,
              matchedOrdersRemove.map(_.trade.existingOrder)
            )
          }
      }

    case Command.RegisterPublicKey(currency, publicKey) =>
      processResponseF {
        ctx.peerUser match {
          case user: PeerUser.Bot => peerActorOps.registerPublicKey(user.id, publicKey, currency)
          case user: PeerUser.Wallet => peerActorOps.registerPublicKey(user.id, publicKey, currency)
          case _ => Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
        }
      }

    case Command.RegisterPublicIdentifier(currency, publicIdentifier) =>
      processResponseF {
        ctx.peerUser match {
          case user: PeerUser.Bot => peerActorOps.registerPublicIdentifier(user.id, publicIdentifier, currency)
          case user: PeerUser.Wallet => peerActorOps.registerPublicIdentifier(user.id, publicIdentifier, currency)
          case _ => Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
        }
      }
  }
}
