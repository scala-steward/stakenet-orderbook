package io.stakenet.orderbook.repositories.reports

import java.sql.Connection
import java.time.Instant

import anorm._
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.reports._
import org.postgresql.util.{PSQLException, PSQLState}

private[reports] object ReportsDAO {

  object Constraints {
    val orderFeePaymentsPK = "order_fee_payments_pk"
    val partialOrdersPK = "partial_orders_pk"
    val partialOrdersFeePaymentsFK = "partial_orders_order_fee_payments_fk"
    val channelRentalFeesFK = "channel_rental_fees_pk"
    val feeRefundReportsFK = "fee_refund_reports_pk"
    val channelRentalExtensionFeesPK = "channel_rental_extension_fees_pk"
    val channelRentalFeesDetailPK = "channel_rental_fees_detail_pk"
  }

  def createOrderFeePayment(orderFeePayment: OrderFeePayment)(
      implicit conn: Connection
  ): Unit = {
    try {
      val paymentHash = orderFeePayment.paymentRHash.value.toArray
      val _ = SQL"""
        INSERT INTO order_fee_payments(payment_hash, currency, funds_amount, fee_amount, fee_percent, created_at)
        VALUES (
          $paymentHash,
          ${orderFeePayment.currency.entryName}::CURRENCY_TYPE,
          ${orderFeePayment.fundsAmount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${orderFeePayment.feeAmount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${orderFeePayment.feePercent},
          ${orderFeePayment.createdAt}
        )
        """
        .execute()
    } catch {
      case e: PSQLException if isDuplicatedOrderFeePaymentError(e) =>
        throw new PSQLException("Order fee payment already exists", PSQLState.DATA_ERROR)
    }
  }

  def createPartialOrder(partialOrder: PartialOrder)(
      implicit conn: Connection
  ): Unit = {
    try {
      val paymentHash = partialOrder.paymentHash.map(_.value.toArray)
      val _ = SQL"""
       INSERT INTO partial_orders(order_id, client_id, payment_hash, currency, traded_amount, created_at)
        VALUES (
          ${partialOrder.orderId.toString}::UUID,
          ${partialOrder.ownerId.toString}::UUID,
          $paymentHash,
          ${partialOrder.currency.entryName}::CURRENCY_TYPE,
          ${partialOrder.tradedAmount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${partialOrder.createdAt}
        )
        """
        .execute()
    } catch {
      case e: PSQLException if isDuplicatedPartialOrderError(e) =>
        throw new PSQLException("Partial order already exists", PSQLState.DATA_ERROR)
    }
  }

  def createChannelRentalFee(channelRentalFee: ChannelRentalFee)(
      implicit conn: Connection
  ): Unit = {
    try {
      val paymentHash = channelRentalFee.paymentHash.value.toArray
      val fundingTxId = channelRentalFee.fundingTransaction.bigEndianBytes.toArray
      val closingTxId = channelRentalFee.closingTransaction.bigEndianBytes.toArray

      val _ = SQL"""
      INSERT INTO channel_rental_fees(
	      payment_hash,
        paying_currency,
        rented_currency,
        fee_amount,
        capacity,
        funding_transaction,
        funding_transaction_fee,
        closing_transaction,
        closing_transaction_fee,
        created_at,
        life_time_seconds)
        VALUES (
          $paymentHash,
          ${channelRentalFee.payingCurrency.entryName}::CURRENCY_TYPE,
          ${channelRentalFee.rentedCurrency.entryName}::CURRENCY_TYPE,
          ${channelRentalFee.feeAmount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${channelRentalFee.capacity.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          $fundingTxId,
          ${channelRentalFee.fundingTransactionFee.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          $closingTxId,
          ${channelRentalFee.closingTransaction_fee.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${channelRentalFee.createdAt},
          ${channelRentalFee.lifeTimeSeconds}
          
        )
        """
        .execute()
    } catch {
      case e: PSQLException if isDuplicatedChannelRentalFeesFKError(e) =>
        throw new PSQLException("Channel rental fee already exists", PSQLState.DATA_ERROR)
    }
  }

  def createFeeRefundedReport(feeRefundsReport: FeeRefundsReport)(
      implicit conn: Connection
  ): Unit = {
    try {

      val _ = SQL"""
      INSERT INTO fee_refunds_reports(
        fee_refund_id,
        currency,
        amount,
        refunded_on)
      VALUES (
        ${feeRefundsReport.refundId.uuid}::UUID,
        ${feeRefundsReport.currency.entryName}::CURRENCY_TYPE,
        ${feeRefundsReport.amount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
        ${feeRefundsReport.refundedOn}
      )
        """
        .execute()
    } catch {
      case e: PSQLException if isDuplicatedFeeRefundReportFKError(e) =>
        throw new PSQLException("Refunded fee report already exists", PSQLState.DATA_ERROR)
    }
  }

  def getChannelRentRevenue(currency: Currency, from: Instant, to: Instant)(
      implicit conn: Connection
  ): ChannelRentRevenue = {
    SQL"""
        SELECT
          f.paying_currency,
          SUM(COALESCE(d.renting_fee, 0)) as renting_fee,
          SUM(COALESCE(d.transaction_fee, 0)) as transaction_fee,
          SUM(COALESCE(d.force_closing_fee, 0)) as force_closing_fee
        FROM channel_rental_fees f
        LEFT JOIN channel_rental_fee_details d ON f.payment_hash = d.payment_hash AND f.paying_currency = d.currency
        WHERE created_at BETWEEN $from AND $to
            AND paying_currency = ${currency.entryName}::CURRENCY_TYPE
        GROUP BY paying_currency;
         """.as(ReportsParsers.channelRevenueParser.singleOpt).getOrElse(ChannelRentRevenue.empty(currency))
  }

  def getChannelTransactionFees(currency: Currency, from: Instant, to: Instant)(
      implicit conn: Connection
  ): ChannelTransactionFee = {
    SQL"""
       SELECT
            rented_currency,
            SUM(COALESCE(funding_transaction_fee, 0)) as funding_tx_fee,
            SUM(COALESCE(closing_transaction_fee, 0)) as closing_tx_fee,
            count(*) as num_rentals,
            SUM(COALESCE(life_time_seconds, 0)) as life_time_seconds,
            SUM(COALESCE(capacity, 0)) as total_capacity
        FROM channel_rental_fees
        WHERE created_at BETWEEN $from AND $to
            AND rented_currency = ${currency.entryName}::CURRENCY_TYPE
        GROUP BY rented_currency;
         """.as(ReportsParsers.channelTransactionFeeParser.singleOpt).getOrElse(ChannelTransactionFee.empty(currency))
  }

  def getTradesFeeReport(currency: Currency, from: Instant, to: Instant)(
      implicit conn: Connection
  ): TradesFeeReport = {
    SQL"""
      SELECT currency,
        COALESCE
        ((
          SELECT SUM(fee_amount) 
          FROM order_fee_payments
          WHERE created_at BETWEEN $from AND $to
            AND currency = ${currency.entryName}::CURRENCY_TYPE
          GROUP BY currency),0) AS fee,
        COUNT(*) AS total_orders,
        SUM(COALESCE(traded_amount, 0)) AS volume,
        COALESCE
        ((
          SELECT SUM(amount) 
          FROM fee_refunds_reports
          WHERE refunded_on >= $from
            AND refunded_on <= $to
            AND currency = ${currency.entryName}::CURRENCY_TYPE
          GROUP BY currency
         ),0) AS refunded_fee
      FROM partial_orders
      WHERE created_at >= $from
        AND created_at <= $to
        AND currency = ${currency.entryName}::CURRENCY_TYPE
      GROUP BY currency, refunded_fee;
         """
      .as(ReportsParsers.tradesFeeReportParser.singleOpt)
      .getOrElse(TradesFeeReport.empty(currency))
  }

  def createChannelRentalExtensionFee(fee: ChannelRentalExtensionFee)(
      implicit conn: Connection
  ): Unit = {
    try {
      val paymentHash = fee.paymentHash.value.toArray

      val _ = SQL"""
        INSERT INTO channel_rental_extension_fees(
          payment_hash,
          paying_currency,
          rented_currency,
          amount,
          created_at
        ) VALUES (
          $paymentHash,
          ${fee.payingCurrency.entryName}::CURRENCY_TYPE,
          ${fee.rentedCurrency.entryName}::CURRENCY_TYPE,
          ${fee.amount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${fee.createdAt}
        )
      """.execute()
    } catch {
      case e: PSQLException if isDuplicatedChannelRentalExtensionFee(e) =>
        throw new PSQLException(s"Channel rental extension fee ${fee.paymentHash} already exists", PSQLState.DATA_ERROR)
    }
  }

  def getChannelRentExtensionsRevenue(currency: Currency, from: Instant, to: Instant)(
      implicit conn: Connection
  ): ChannelRentExtensionsRevenue = {
    SQL"""
         SELECT
           paying_currency,
           SUM(amount) as revenue
         FROM channel_rental_extension_fees
         WHERE created_at >= $from
           AND created_at <= $to
           AND paying_currency = ${currency.entryName}::CURRENCY_TYPE
         GROUP BY paying_currency;
       """
      .as(ReportsParsers.channelRentExtensionsRevenueParser.singleOpt)
      .getOrElse(ChannelRentExtensionsRevenue.empty(currency))
  }

  def getChannelRentExtensionsCount(currency: Currency, from: Instant, to: Instant)(implicit conn: Connection): Int = {
    SQL"""
         SELECT
           COUNT(*) as extensions
         FROM channel_rental_extension_fees
         WHERE created_at >= $from
           AND created_at <= $to
           AND rented_currency = ${currency.entryName}::CURRENCY_TYPE
         GROUP BY rented_currency;
       """
      .as(SqlParser.scalar[Int].singleOpt)
      .getOrElse(0)
  }

  def createChannelRentalFeeDetail(detail: ChannelRentalFeeDetail)(implicit conn: Connection): Unit = {
    try {
      val paymentHash = detail.paymentHash.value.toArray

      val _ = SQL"""
        INSERT INTO channel_rental_fee_details(
          payment_hash,
          currency,
          renting_fee,
          transaction_fee,
          force_closing_fee
        ) VALUES (
          $paymentHash,
          ${detail.currency.entryName}::CURRENCY_TYPE,
          ${detail.rentingFee.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${detail.transactioFee.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${detail.forceClosingFee.value(Satoshis.Digits)}::SATOSHIS_TYPE
        )
      """.execute()
    } catch {
      case e: PSQLException if isDuplicatedChannelRentalFeeDetail(e) =>
        throw new PSQLException(
          s"Channel rental fee detail ${detail.paymentHash} for ${detail.currency} already exists",
          PSQLState.DATA_ERROR
        )
    }
  }

  def getClientsStatusReport()(implicit conn: Connection): List[ClientStatus] = {
    SQL"""
      SELECT
        c.client_id,
        CASE
          WHEN wc.client_id IS NOT NULL THEN 'Wallet'
          WHEN bmc.client_id IS NOT NULL THEN 'Bot'
          ELSE 'Unknown'
        END AS client_type,
        currentStatus.rented_capacity_usd,
        currentStatus.hub_local_balance_usd,
        CASE
          WHEN currentStatus.rented_capacity_usd >= currentStatus.hub_local_balance_usd THEN 'GREEN'
          ELSE 'RED'
        END AS status,
        EXTRACT(
          EPOCH FROM (
            CASE
              WHEN previousStatus.client_info_log_id IS NOT NULL THEN currentStatus.created_at - previousStatus.created_at
              ELSE currentStatus.created_at - firstStatus.created_at
            END
          )
        ) AS time
      FROM clients c
      INNER JOIN LATERAL (
        SELECT
          *
        FROM client_info_logs l
        WHERE l.client_id = c.client_id
        ORDER BY l.created_at DESC
        LIMIT 1
      ) currentStatus ON true
      LEFT JOIN LATERAL (
        SELECT
          *
        FROM client_info_logs l
        WHERE l.client_id = c.client_id
          AND (currentStatus.rented_capacity_usd >= currentStatus.hub_local_balance_usd) !=
            (l.rented_capacity_usd >= l.hub_local_balance_usd)
        ORDER BY l.created_at DESC
        LIMIT 1
      ) previousStatus ON true
      INNER JOIN LATERAL (
        SELECT
          *
        FROM client_info_logs l
        WHERE l.client_id = c.client_id
        ORDER BY l.created_at ASC
        LIMIT 1
      ) firstStatus ON true
      LEFT JOIN bot_maker_clients bmc ON c.client_id = bmc.client_id
      LEFT JOIN wallet_clients wc ON c.client_id = wc.client_id
      WHERE currentStatus.rented_capacity_usd <> 0 OR
        currentStatus.hub_local_balance_usd <> 0
      ORDER BY c.client_id;
       """.as(ReportsParsers.clientStatusParser.*)
  }

  private def isDuplicatedOrderFeePaymentError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.orderFeePaymentsPK
  }

  private def isDuplicatedPartialOrderError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.partialOrdersPK
  }

  private def isDuplicatedChannelRentalFeesFKError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.channelRentalFeesFK
  }

  private def isDuplicatedFeeRefundReportFKError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.feeRefundReportsFK
  }

  private def isDuplicatedChannelRentalExtensionFee(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.channelRentalExtensionFeesPK
  }

  private def isDuplicatedChannelRentalFeeDetail(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.channelRentalFeesDetailPK
  }
}
