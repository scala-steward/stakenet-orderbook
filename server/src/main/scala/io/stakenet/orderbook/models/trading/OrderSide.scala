package io.stakenet.orderbook.models.trading

import enumeratum._

sealed trait OrderSide extends EnumEntry

object OrderSide extends Enum[OrderSide] {

  val values = findValues

  case object Buy extends OrderSide
  case object Sell extends OrderSide
}
