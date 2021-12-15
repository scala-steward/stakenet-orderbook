package io.stakenet.orderbook.models.liquidityProviders

import java.util.UUID

case class LiquidityProviderLogId(uuid: UUID) {
  override def toString: String = uuid.toString
}

object LiquidityProviderLogId {
  def random(): LiquidityProviderLogId = LiquidityProviderLogId(UUID.randomUUID())
}
