package io.stakenet.orderbook.actors.peers.handlers

import akka.event.LoggingAdapter
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CommandFailed
import io.stakenet.orderbook.actors.peers.{PeerActorOps, PeerUser}
import io.stakenet.orderbook.actors.peers.protocol.{ChannelCommand, Command}

import scala.concurrent.{ExecutionContext, Future}

class ChannelCommandHandler(peerActorOps: PeerActorOps)(implicit ec: ExecutionContext)
    extends CommandHandler[ChannelCommand] {

  override def handle(cmd: ChannelCommand)(implicit ctx: CommandContext, log: LoggingAdapter): CommandHandler.Result = {
    cmd match {
      case Command.GenerateInvoiceToRentChannel(channelFeePayment) =>
        processResponseF {
          peerActorOps.generateInvoiceToRentChannel(channelFeePayment)
        }

      case Command.GeneratePaymentHashToRentChannel(channelFeePayment) =>
        processResponseF {
          peerActorOps.generateConnextRentPayment(channelFeePayment)
        }

      case Command.RentChannel(paymentHash, payingCurrency) =>
        processResponseF {
          ctx.peerUser match {
            case user: PeerUser.Bot => peerActorOps.rentChannel(user.id, paymentHash, payingCurrency)
            case user: PeerUser.Wallet => peerActorOps.rentChannel(user.id, paymentHash, payingCurrency)
            case _ => Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
          }
        }

      case Command.GetChannelStatus(channelId) =>
        processResponseF {
          peerActorOps.getChannelStatus(channelId)
        }

      case Command.GetFeeToRentChannel(channelFeePayment) =>
        processResponseF {
          peerActorOps.getFeeToRentChannel(channelFeePayment)
        }
      case Command.GetFeeToExtendRentedChannel(channelId, payingCurrency, lifetimeSeconds) =>
        processResponseF {
          peerActorOps.getFeeToExtendRentedChannel(channelId, payingCurrency, lifetimeSeconds)
        }
      case Command.GenerateInvoiceToExtendRentedChannel(channelId, payingCurrency, lifetimeSeconds) =>
        processResponseF {
          peerActorOps.generateInvoiceToExtendRentedChannel(channelId, payingCurrency, lifetimeSeconds)
        }
      case Command.GeneratePaymentHashToExtendConnextRentedChannel(channelId, payingCurrency, lifetimeSeconds) =>
        processResponseF {
          peerActorOps.generatePaymentHashToExtendRentedChannel(channelId, payingCurrency, lifetimeSeconds)
        }
      case Command.ExtendRentedChannelTime(paymentHash, payingCurrency) =>
        processResponseF {
          ctx.peerUser match {
            case user: PeerUser.Bot => peerActorOps.extendRentedChannel(user.id, paymentHash, payingCurrency)
            case user: PeerUser.Wallet => peerActorOps.extendRentedChannel(user.id, paymentHash, payingCurrency)
            case _ => Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
          }
        }
      case Command.RegisterConnextChannelContractDeploymentFee(transactionHash) =>
        processResponseF {
          ctx.peerUser match {
            case bot: PeerUser.Bot =>
              peerActorOps.createConnextChannelContractDeploymentFee(transactionHash, bot.id)

            case wallet: PeerUser.Wallet =>
              peerActorOps.createConnextChannelContractDeploymentFee(transactionHash, wallet.id)

            case _ =>
              Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
          }
        }
      case Command.GetConnextChannelContractDeploymentFee() =>
        processResponseF {
          ctx.peerUser match {
            case user: PeerUser.Bot => peerActorOps.getConnextChannelContractDeploymentFee(user.id)
            case user: PeerUser.Wallet => peerActorOps.getConnextChannelContractDeploymentFee(user.id)
            case _ => Future.successful(CommandFailed(s"Operation not supported for ${ctx.peerUser}"))
          }
        }
    }
  }
}
