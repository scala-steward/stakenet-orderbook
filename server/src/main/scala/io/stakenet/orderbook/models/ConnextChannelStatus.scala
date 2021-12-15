package io.stakenet.orderbook.models

import enumeratum.EnumEntry.Uppercase
import enumeratum._

sealed trait ConnextChannelStatus extends EnumEntry with Product with Serializable with Uppercase

object ConnextChannelStatus extends Enum[ConnextChannelStatus] {
  val values = findValues

  final case object Confirming extends ConnextChannelStatus
  final case object Active extends ConnextChannelStatus
  final case object Closed extends ConnextChannelStatus
}
