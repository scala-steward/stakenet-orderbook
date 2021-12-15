package io.stakenet.orderbook.repositories.fees

import anorm.{Macro, RowParser}
import io.stakenet.orderbook.models.lnd.{Fee, FeeInvoice}
import io.stakenet.orderbook.repositories.CommonParsers._

private[fees] object FeeParsers {

  val feesPaymentParser: RowParser[Fee] = Macro.parser[Fee](
    "currency",
    "r_hash",
    "amount",
    "locked_for_order_id",
    "paid_at",
    "fee_percent"
  )

  val invoiceParser: RowParser[FeeInvoice] = Macro.parser[FeeInvoice](
    "payment_hash",
    "currency",
    "amount",
    "requested_at"
  )
}
