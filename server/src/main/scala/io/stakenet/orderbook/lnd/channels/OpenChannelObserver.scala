package io.stakenet.orderbook.lnd.channels

import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.lnd.channels.OpenChannelObserver.Exceptions
import io.stakenet.orderbook.models.Channel.LndChannel
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd.LndTxid
import io.stakenet.orderbook.models.{Currency, Satoshis}
import lnrpc.rpc.OpenStatusUpdate
import lnrpc.rpc.OpenStatusUpdate.Update
import org.slf4j.LoggerFactory

class OpenChannelObserver(channel: LndChannel, currency: Currency, capacity: Satoshis, discordHelper: DiscordHelper)(
    _onChannelPending: LndOutpoint => Unit,
    _onChannelOpened: LndTxid => Unit,
    _onError: Throwable => Unit
) extends StreamObserver[OpenStatusUpdate] {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val nodePublicKey = channel.publicKey

  override def onCompleted(): Unit = {
    logger.info(s"Completed with node publicKey: $nodePublicKey")
  }

  override def onError(exception: Throwable): Unit = {
    logger.warn(s"An error occurred to open the channel to $nodePublicKey with id = ${channel.channelId}", exception)

    val newException = exception match {
      case e: StatusRuntimeException if e.getMessage.startsWith("UNKNOWN: not enough witness outputs") =>
        val msj = e.getMessage.replaceFirst(
          "UNKNOWN: not enough witness outputs to create funding transaction",
          s"Hub doesn't have enough funds to open a channel of ${capacity.toString(currency)}"
        )
        logger.warn(msj)
        discordHelper.sendMessage(s"@everyone $msj")
        new Exceptions.NotEnoughFunds()
      case e: StatusRuntimeException if e.getMessage == s"UNKNOWN: peer $nodePublicKey is not online" =>
        new Exceptions.OfflinePeer(nodePublicKey)
      case e: Exception =>
        e
    }

    _onError(newException)
  }

  override def onNext(value: OpenStatusUpdate): Unit = {
    logger.info(
      s"Message received, publicKey = ${nodePublicKey}, channelId = ${channel.channelId}, message = ${value.toProtoString}"
    )
    value.update match {
      case Update.Empty => ()
      case Update.ChanPending(value) =>
        val txid = LndTxid.fromLnd(value.txid.toByteArray)

        val index = value.outputIndex
        _onChannelPending(LndOutpoint(txid, index))

      case Update.ChanOpen(x) =>
        val txid = x.getChannelPoint.fundingTxid.fundingTxidBytes
          .map(x => LndTxid.fromLnd(x.toByteArray))
          .getOrElse(throw new RuntimeException("invalid funding transaction received from lnd"))
        _onChannelOpened(txid)

      case Update.PsbtFund(_) => ()
    }
  }
}

object OpenChannelObserver {

  object Exceptions {

    class NotEnoughFunds()
        extends RuntimeException("Not enough available funds on this HUB. Please try a smaller amount")
    class OfflinePeer(peer: Identifier.LndPublicKey) extends RuntimeException(s"$peer is offline")
  }
}
