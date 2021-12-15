package io.stakenet.orderbook.models.liquidityProviders

import enumeratum.EnumEntry.Uppercase
import enumeratum._

sealed trait LiquidityProviderLogType extends EnumEntry with Uppercase

object LiquidityProviderLogType extends Enum[LiquidityProviderLogType] {
  val values = findValues

  final case object Joined extends LiquidityProviderLogType
  final case object Left extends LiquidityProviderLogType
  final case object Bought extends LiquidityProviderLogType
  final case object Sold extends LiquidityProviderLogType
  final case object Fee extends LiquidityProviderLogType
}
