package io.stakenet.orderbook.models.liquidityProviders

import java.util.UUID

case class LiquidityProviderId(uuid: UUID) {
  override def toString: String = uuid.toString
}

object LiquidityProviderId {
  def random(): LiquidityProviderId = LiquidityProviderId(UUID.randomUUID())
}
