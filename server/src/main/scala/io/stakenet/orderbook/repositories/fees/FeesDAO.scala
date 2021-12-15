package io.stakenet.orderbook.repositories.fees

import java.sql.Connection

import anorm._
import io.stakenet.orderbook.models.lnd.{Fee, FeeInvoice, PaymentRHash}
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.fees.requests.LinkFeeToOrderRequest
import org.postgresql.util.{PSQLException, PSQLState}

private[repositories] object FeesDAO {

  object Constraints {
    val FEES_PK = "fees_pk"
    val ORDER_FEE_INVOICES_PK = "order_fee_invoices_pk"
  }

  def createInvoice(feeInvoice: FeeInvoice)(implicit conn: Connection): Unit = {
    try {
      val hash = feeInvoice.paymentHash.value.toArray

      SQL"""
           INSERT INTO order_fee_invoices(payment_hash, currency, amount, requested_at)
           VALUES(
             $hash,
             ${feeInvoice.currency.entryName}::CURRENCY_TYPE,
             ${feeInvoice.amount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
             ${feeInvoice.requestedAt}
           )
         """
        .execute()

      ()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.ORDER_FEE_INVOICES_PK) =>
        throw new PSQLException("invalid paymentHash", PSQLState.DATA_ERROR)
    }
  }

  def findInvoice(paymentHash: PaymentRHash, currency: Currency)(implicit conn: Connection): Option[FeeInvoice] = {
    val hash = paymentHash.value.toArray

    SQL"""
         SELECT payment_hash, currency, amount, requested_at
         FROM order_fee_invoices
         WHERE payment_hash = $hash AND
               currency = ${currency.entryName}::CURRENCY_TYPE
       """
      .as(FeeParsers.invoiceParser.singleOpt)
  }

  def create(request: LinkFeeToOrderRequest)(implicit conn: Connection): Unit = {
    try {
      val hashValue = request.hash.value.toArray
      val _ = SQL"""
        INSERT INTO fees
          (r_hash, currency, amount, locked_for_order_id, paid_at, fee_percent)
        VALUES (
          $hashValue,
          ${request.currency.entryName}::CURRENCY_TYPE,
          ${request.amount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${request.orderId.toString}::UUID,
          ${request.paidAt},
          ${request.feePercent}
        )
        """
        .execute()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.FEES_PK) =>
        throw new PSQLException("invalid paymentHash", PSQLState.DATA_ERROR)
    }
  }

  def find(hash: PaymentRHash, currency: Currency)(implicit conn: Connection): Option[Fee] = {
    val hashValue = hash.value.toArray
    SQL"""
        SELECT r_hash, currency, amount, locked_for_order_id, paid_at, fee_percent
        FROM fees
        WHERE r_hash = $hashValue AND
              currency = ${currency.entryName}::CURRENCY_TYPE
     """.as(FeeParsers.feesPaymentParser.singleOpt)
  }

  def findForUpdate(hash: PaymentRHash, currency: Currency)(implicit conn: Connection): Option[Fee] = {
    val hashValue = hash.value.toArray
    SQL"""
        SELECT r_hash, currency, amount, locked_for_order_id, paid_at, fee_percent
        FROM fees
        WHERE r_hash = $hashValue AND
              currency = ${currency.entryName}::CURRENCY_TYPE
        FOR UPDATE NOWAIT
     """.as(FeeParsers.feesPaymentParser.singleOpt)
  }

  def find(orderId: OrderId, currency: Currency)(implicit conn: Connection): Option[Fee] = {
    SQL"""
        SELECT r_hash, currency, amount, locked_for_order_id, paid_at, fee_percent
        FROM fees
        WHERE locked_for_order_id = ${orderId.value.toString}::UUID AND
              currency = ${currency.entryName}::CURRENCY_TYPE
     """.as(FeeParsers.feesPaymentParser.singleOpt)
  }

  def findForUpdate(orderId: OrderId, currency: Currency)(implicit conn: Connection): Option[Fee] = {
    SQL"""
        SELECT r_hash, currency, amount, locked_for_order_id, paid_at, fee_percent
        FROM fees
        WHERE locked_for_order_id = ${orderId.value.toString}::UUID AND
              currency = ${currency.entryName}::CURRENCY_TYPE
        FOR UPDATE NOWAIT
     """.as(FeeParsers.feesPaymentParser.singleOpt)
  }

  def lock(hash: PaymentRHash, currency: Currency, orderId: OrderId)(implicit connection: Connection): Unit = {
    val hashArray = hash.value.toArray
    val updatedRows = SQL"""
         UPDATE fees
         SET locked_for_order_id = ${orderId.value.toString}::UUID
         WHERE r_hash = $hashArray AND
               currency = ${currency.entryName}::CURRENCY_TYPE AND
               locked_for_order_id IS NULL
       """.executeUpdate()

    assert(
      updatedRows == 1,
      s"The fee wasn't locked likely due to a race condition, hash = $hash, currency = $currency, orderId = $orderId"
    )
  }

  def free(hash: PaymentRHash, currency: Currency)(implicit connection: Connection): Unit = {
    val hashArray = hash.value.toArray
    val updatedRows = SQL"""
         UPDATE fees
         SET locked_for_order_id = NULL
         WHERE r_hash = $hashArray AND
               currency = ${currency.entryName}::CURRENCY_TYPE
       """.executeUpdate()

    assert(
      updatedRows == 1,
      s"The fee wasn't freed likely due to a race condition, hash = $hash, currency = $currency"
    )
  }

  def free(orderId: OrderId)(implicit connection: Connection): Unit = {
    val _ = SQL"""
         UPDATE fees
         SET locked_for_order_id = NULL
         WHERE locked_for_order_id = ${orderId.value.toString}::UUID
       """.executeUpdate()
  }

  def freeAll(implicit connection: Connection): Unit = {
    val _ = SQL"""
        UPDATE fees
        SET locked_for_order_id = null
        WHERE locked_for_order_id IS NOT null
       """.executeUpdate()
  }

  def burn(orderId: OrderId, currency: Currency, newAmount: Satoshis)(implicit conn: Connection): Unit = {
    val updatedRows = SQL"""
        UPDATE fees
        SET amount = ${newAmount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
            locked_for_order_id = NULL
        WHERE locked_for_order_id = ${orderId.toString}::UUID
          AND currency = ${currency.entryName}::CURRENCY_TYPE
        """
      .executeUpdate()

    assert(
      updatedRows == 1,
      s"The fee wasn't burned, likely due to a race condition, orderId = $orderId, currency = $currency, newAmount = $newAmount"
    )
  }

  def refund(hash: PaymentRHash, currency: Currency)(implicit conn: Connection): Unit = {
    val hashArray = hash.value.toArray

    val updatedRows = SQL"""
        UPDATE fees
        SET amount = 0::SATOSHIS_TYPE,
            locked_for_order_id = NULL
        WHERE r_hash = $hashArray
          AND currency = ${currency.entryName}::CURRENCY_TYPE
        """
      .executeUpdate()

    assert(
      updatedRows == 1,
      s"The fee wasn't refunded, likely due to a race condition, hash = $hash, currency = $currency"
    )
  }

  private def violatesConstraint(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
