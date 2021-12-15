package io.stakenet.orderbook.models.lnd

import enumeratum.EnumEntry.Uppercase
import enumeratum._

sealed trait RefundStatus extends EnumEntry with Product with Serializable

object RefundStatus extends Enum[RefundStatus] {
  val values = findValues

  final case object Processing extends RefundStatus with Uppercase
  final case object Refunded extends RefundStatus with Uppercase
  final case object Failed extends RefundStatus with Uppercase
}
