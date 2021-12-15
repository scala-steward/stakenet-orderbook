package io.stakenet.orderbook.actors.peers.handlers

import akka.event.LoggingAdapter
import io.stakenet.orderbook.actors.peers.{PeerActorOps, PeerUser}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CommandFailed
import io.stakenet.orderbook.actors.peers.protocol.{Command, OrderFeeCommand}

import scala.concurrent.{ExecutionContext, Future}

class OrderFeeCommandHandler(peerActorOps: PeerActorOps)(implicit ec: ExecutionContext)
    extends CommandHandler[OrderFeeCommand] {

  override def handle(
      cmd: OrderFeeCommand
  )(implicit ctx: CommandContext, log: LoggingAdapter): CommandHandler.Result = {
    cmd match {
      case Command.GetInvoicePayment(currency, amount) =>
        processResponseF {
          peerActorOps.generateOrderFeeInvoice(currency, amount)
        }
      case Command.GetConnextPaymentInformation(currency) =>
        processResponseF {
          peerActorOps.getConnextPaymentInformation(currency)
        }
      case Command.GetRefundableAmount(currency, refundablePaymentList) =>
        processResponseF {
          peerActorOps.getRefundableAmount(currency, refundablePaymentList)
        }
      case Command.RefundFee(currency, refundedFees) =>
        processResponseF {
          ctx.peerUser match {
            case user: PeerUser.Bot => peerActorOps.refundFee(user.id, currency, refundedFees)
            case user: PeerUser.Wallet => peerActorOps.refundFee(user.id, currency, refundedFees)
            case _ => Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
          }
        }
    }
  }
}
