package io.stakenet.orderbook.actors.peers.handlers

import akka.event.LoggingAdapter
import akka.pattern.ask
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers.protocol.{Command, Event, SubscriptionCommand}

import scala.concurrent.ExecutionContext

class SubscriptionCommandHandler(orderManager: OrderManagerActor.Ref)(implicit ec: ExecutionContext)
    extends CommandHandler[SubscriptionCommand] {

  import CommandHandler._

  override def handle(cmd: SubscriptionCommand)(implicit ctx: CommandContext, log: LoggingAdapter): Result = cmd match {
    case Command.Subscribe(pair, includeOrderSummary) =>
      processResponseF {
        (orderManager.ref ? OrderManagerActor.Command.Subscribe(pair, ctx.self, includeOrderSummary))
          .mapTo[OrderManagerActor.Event.Subscribed]
          .map { x =>
            Event.CommandResponse.SubscribeResponse(x.pair, x.bidsSummary, x.asksSummary)
          }
      }

    case Command.Unsubscribe(pair) =>
      processResponseF {
        (orderManager.ref ? OrderManagerActor.Command.Unsubscribe(pair, ctx.self))
          .mapTo[OrderManagerActor.Event.Unsubscribed]
          .map { _ =>
            Event.CommandResponse.UnsubscribeResponse(pair)
          }
      }
  }
}
