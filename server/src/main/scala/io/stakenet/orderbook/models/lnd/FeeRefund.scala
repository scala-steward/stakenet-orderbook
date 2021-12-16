package io.stakenet.orderbook.models.lnd

import java.time.Instant
import java.util.UUID

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class FeeRefund(
    id: FeeRefund.Id,
    currency: Currency,
    amount: Satoshis,
    status: RefundStatus,
    requestedAt: Instant,
    refundedOn: Option[Instant]
)

object FeeRefund {

  case class Id(uuid: UUID) {
    override def toString: String = uuid.toString
  }

  object Id {
    def random(): FeeRefund.Id = FeeRefund.Id(UUID.randomUUID())
  }
}
