package io.stakenet.orderbook.repositories.feeRefunds

import anorm.{Column, Macro, RowParser}
import io.stakenet.orderbook.models.lnd.{FeeRefund, RefundStatus}
import io.stakenet.orderbook.repositories.CommonParsers._

private[feeRefunds] object FeeRefundParsers {

  implicit val statusColumn: Column[RefundStatus] = enumColumn(RefundStatus.withNameInsensitiveOption)
  implicit val idColumn: Column[FeeRefund.Id] = Column.columnToUUID.map(FeeRefund.Id.apply)

  val feeRefundParser: RowParser[FeeRefund] =
    Macro.parser[FeeRefund](
      "fee_refund_id",
      "currency",
      "amount",
      "status",
      "requested_at",
      "refunded_on"
    )
}
