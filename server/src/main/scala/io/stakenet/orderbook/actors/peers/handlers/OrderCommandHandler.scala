package io.stakenet.orderbook.actors.peers.handlers

import akka.event.LoggingAdapter
import akka.pattern.ask
import io.stakenet.lssd.protos.swap_packets.Packet
import io.stakenet.lssd.protos.swap_packets.Packet.Swap
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CommandFailed
import io.stakenet.orderbook.actors.peers.{PeerActorOps, PeerUser}
import io.stakenet.orderbook.actors.peers.protocol.{Command, Event, OrderCommand}
import io.stakenet.orderbook.models.OrderMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class OrderCommandHandler(
    orderManager: OrderManagerActor.Ref,
    peerActorOps: PeerActorOps
)(implicit ec: ExecutionContext)
    extends CommandHandler[OrderCommand] {
  import CommandHandler._

  override def handle(cmd: OrderCommand)(implicit ctx: CommandContext, log: LoggingAdapter): Result = cmd match {
    case Command.PlaceOrder(order, paymentHash) =>
      processResponseF {
        ctx.peerUser match {
          case user: PeerUser.Bot =>
            peerActorOps.placeOrder(order, paymentHash, user.id, ctx.self)(ctx.state, ctx.peerUser, defaultTimeout)
          case user: PeerUser.Wallet =>
            peerActorOps.placeOrder(order, paymentHash, user.id, ctx.self)(ctx.state, ctx.peerUser, defaultTimeout)
          case _ =>
            Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))

        }
      }

    case Command.GetOpenOrderById(orderId) =>
      processResponseF {
        (orderManager.ref ? OrderManagerActor.Command.GetTradingOrderById(orderId))
          .mapTo[Event.CommandResponse.GetOpenOrderByIdResponse]
      }

    case Command.GetOpenOrders(pair) =>
      processResponseF {
        (orderManager.ref ? OrderManagerActor.Command.GetTradingOrders(pair))
          .mapTo[Event.CommandResponse.GetOpenOrdersResponse]
      }

    case Command.CancelOpenOrder(id) =>
      processResponseF {
        ctx.state
          .findOpenOrder(id)
          .map { _ =>
            peerActorOps.tryCancelingPayment(id)(log)(ctx.peerUser)
            (orderManager.ref ? OrderManagerActor.Command.CancelOrder(id, ctx.self))
              .mapTo[Event.CommandResponse.CancelOpenOrderResponse]
          }
          .getOrElse {
            // order not found, can't cancel it
            Future.successful(Event.CommandResponse.CancelOpenOrderResponse(None))
          }
      }

    case Command.CancelMatchedOrder(orderId) =>
      val existingOrder = ctx.state.matched.find(_.trade.orders contains orderId)
      // notify peer that its matched order was canceled
      existingOrder.foreach { peerTrade =>
        peerActorOps.tryCancelingPayment(peerTrade.trade.executingOrder)(log)(ctx.peerUser)
        peerActorOps.tryCancelingPayment(peerTrade.trade.existingOrder)(log)(ctx.peerUser)
        peerTrade.secondOrder.peer ! Event.ServerEvent.MyMatchedOrderCanceled(peerTrade.trade)
      }

      // cancel the actual match
      processResponse {
        val tradeMaybe = existingOrder.map(_.trade)
        Event.CommandResponse.CancelMatchedOrderResponse(tradeMaybe)
      }

      // update the state
      val newState = ctx.state.removeMatched(orderId)
      Result.StateUpdated(newState)

    case Command.SendOrderMessage(OrderMessage(message, orderId)) =>
      // TODO: Validate what the message can be to avoid security issues
      val matchedOrder = ctx.state.matched.find(_.trade.orders contains orderId)

      // send the actual message
      matchedOrder.foreach { peerTrade =>
        val otherOrderId = peerTrade.trade.orders
          .find(_ != orderId)
          .getOrElse(throw new RuntimeException("Impossible, the second order wasn't found"))

        val result = Packet.validate(message.toArray).map {
          case Packet(swap, _) =>
            log.info(s"$orderId received a swap message $swap")
            swap match {
              case Swap.Fail(_) =>
                val trade = peerTrade.trade
                log.info(
                  s"${ctx.peerUser.name}: Swap failed on ${trade.pair}: existingOrder = ${trade.existingOrder}, executingOrder = ${trade.executingOrder}, executingSide = ${trade.executingOrderSide}, " +
                    s"size = ${trade.size.toString(trade.sellingCurrency)}"
                )
                peerActorOps.updateSwapFailure(peerTrade, ctx.self)(log)(ctx.peerUser)
              case Swap.Complete(_) =>
                val trade = peerTrade.trade
                log.info(
                  s"${ctx.peerUser.name}: Swap completed on ${trade.pair}: existingOrder = ${trade.existingOrder}, executingOrder = ${trade.executingOrder}, executingSide = ${trade.executingOrderSide}, " +
                    s"size = ${trade.size.toString(trade.sellingCurrency)}"
                )
                peerActorOps.updateSwapSuccess(peerTrade, ctx.self)(log)(ctx.peerUser)
              case message =>
                log.info(s"$orderId received an unknown message $message")
                Future.unit
            }
        }

        processResponseF {
          result match {
            case Failure(error) =>
              log.error(error, "")
              Future.successful(Event.CommandResponse.CommandFailed("The order message is invalid"))
            case Success(value) =>
              value.map { _ =>
                peerTrade.secondOrder.peer ! Event.ServerEvent.NewOrderMessage(otherOrderId, message)
                Event.CommandResponse.SendOrderMessageResponse()
              }
          }
        }
      }

      val _ = matchedOrder.orElse {
        processResponse {
          Event.CommandResponse.CommandFailed("The given order doesn't exists or is not matched")
        }
        None
      }

      Result.Async
  }
}
