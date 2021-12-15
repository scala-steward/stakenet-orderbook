package io.stakenet.orderbook.models.lnd

import enumeratum.EnumEntry.Uppercase
import enumeratum._

sealed trait ChannelStatus extends EnumEntry with Product with Serializable

object ChannelStatus extends Enum[ChannelStatus] {
  val values = findValues

  final case object Opening extends ChannelStatus with Uppercase
  final case object Active extends ChannelStatus with Uppercase
  final case object Closing extends ChannelStatus with Uppercase
  final case object Closed extends ChannelStatus with Uppercase
}
