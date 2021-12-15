package io.stakenet.orderbook.models

import enumeratum._
import enumeratum.EnumEntry.Uppercase

sealed trait MakerPaymentStatus extends EnumEntry with Uppercase

object MakerPaymentStatus extends Enum[MakerPaymentStatus] {
  val values = findValues

  final case object Pending extends MakerPaymentStatus
  final case object Sent extends MakerPaymentStatus
  final case object Failed extends MakerPaymentStatus
}
