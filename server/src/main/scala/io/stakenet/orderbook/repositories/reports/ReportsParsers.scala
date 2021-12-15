package io.stakenet.orderbook.repositories.reports

import anorm.{Column, Macro, RowParser}
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.reports._
import io.stakenet.orderbook.repositories.CommonParsers._

import scala.concurrent.duration._

private[reports] object ReportsParsers {

  implicit val fundingTxidColumn: Column[LndTxid] = Column.columnToByteArray.map(x => LndTxid(x.toVector))
  implicit val finiteDurationColumn: Column[FiniteDuration] = Column.columnToDouble.map(_.seconds)

  val orderFeePaymentParser: RowParser[OrderFeePayment] =
    Macro.parser[OrderFeePayment](
      "payment_hash",
      "currency",
      "funds_amount",
      "fee_amount",
      "fee_percent",
      "created_at"
    )

  val tradesFeeReportParser: RowParser[TradesFeeReport] =
    Macro.parser[TradesFeeReport](
      "currency",
      "fee",
      "volume",
      "total_orders",
      "refunded_fee"
    )

  val channelRevenueParser: RowParser[ChannelRentRevenue] =
    Macro.parser[ChannelRentRevenue](
      "paying_currency",
      "renting_fee",
      "transaction_fee",
      "force_closing_fee"
    )

  val channelTransactionFeeParser: RowParser[ChannelTransactionFee] =
    Macro.parser[ChannelTransactionFee](
      "rented_currency",
      "funding_tx_fee",
      "closing_tx_fee",
      "num_rentals",
      "life_time_seconds",
      "total_capacity"
    )

  val channelRentExtensionsRevenueParser: RowParser[ChannelRentExtensionsRevenue] =
    Macro.parser[ChannelRentExtensionsRevenue](
      "paying_currency",
      "revenue"
    )

  val clientStatusParser: RowParser[ClientStatus] =
    Macro.parser[ClientStatus](
      "client_id",
      "client_type",
      "rented_capacity_usd",
      "hub_local_balance_usd",
      "time"
    )
}
