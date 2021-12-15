package io.stakenet.orderbook.models

import io.stakenet.orderbook.models.lnd.LndTxid

import scala.util.Try

sealed trait ChannelIdentifier

object ChannelIdentifier {

  def apply(channelIdentifier: String): Option[ChannelIdentifier] = {
    LndOutpoint
      .untrusted(channelIdentifier)
      .orElse(ConnextChannelAddress.apply(channelIdentifier))
  }

  final case class LndOutpoint(txid: LndTxid, index: Int) extends ChannelIdentifier {
    override def toString: String = s"$txid:$index"
  }

  object LndOutpoint {

    def untrusted(channelPoint: String): Option[LndOutpoint] = {
      channelPoint.split(':').toList match {
        case txidString :: indexString :: Nil =>
          for {
            txid <- LndTxid.untrusted(txidString)
            index <- Try(indexString.toInt).toOption
          } yield LndOutpoint(txid, index)

        case _ =>
          None
      }
    }
  }

  // Connext channel addresses must start with 0x and are followed by a 20 bytes hex string(although connext API
  // seems to be case sensitive in regards to channel addresses).
  // For example. 0xB8b06869A32976641a41E75beBF647a1B5F05C9e
  final class ConnextChannelAddress private (value: String) extends ChannelIdentifier {
    override def toString: String = value

    override def equals(that: Any): Boolean = that match {
      case that: ConnextChannelAddress => that.toString.equals(this.toString)
      case _ => false
    }
  }

  object ConnextChannelAddress {

    def apply(address: String): Option[ConnextChannelAddress] = {
      Option.when(address.length == 42 && address.startsWith("0x"))(new ConnextChannelAddress(address))
    }
  }
}
