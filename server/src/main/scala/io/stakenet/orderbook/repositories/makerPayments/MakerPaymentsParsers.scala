package io.stakenet.orderbook.repositories.makerPayments

import anorm.{Column, Macro, RowParser}
import io.stakenet.orderbook.models.{MakerPayment, MakerPaymentId, MakerPaymentStatus}
import io.stakenet.orderbook.repositories.CommonParsers._

private[makerPayments] object MakerPaymentsParsers {

  implicit val makerPaymentId: Column[MakerPaymentId] = Column.columnToUUID.map(MakerPaymentId.apply)
  implicit val makerPaymentStatus: Column[MakerPaymentStatus] = enumColumn(MakerPaymentStatus.withNameInsensitiveOption)

  val makerPaymentParser: RowParser[MakerPayment] = Macro.parser[MakerPayment](
    "maker_payment_id",
    "trade_id",
    "client_id",
    "amount",
    "currency",
    "status"
  )
}
