package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.clients.ClientId

import scala.concurrent.duration.FiniteDuration

case class ClientStatus(
    clientId: ClientId,
    clientType: String,
    rentedCapacityUSD: BigDecimal,
    hubLocalBalanceUSD: BigDecimal,
    time: FiniteDuration
) {
  def isGreen = rentedCapacityUSD >= hubLocalBalanceUSD
  def isRed = rentedCapacityUSD < hubLocalBalanceUSD
}
