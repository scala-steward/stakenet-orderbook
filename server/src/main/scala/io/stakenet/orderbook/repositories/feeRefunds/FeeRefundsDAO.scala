package io.stakenet.orderbook.repositories.feeRefunds

import java.sql.Connection

import anorm._
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.lnd.{FeeRefund, PaymentRHash}
import org.postgresql.util.{PSQLException, PSQLState}

private[repositories] object FeeRefundsDAO {

  object Constraints {
    val FeeRefundPK = "fee_refunds_pk"
    val FeeRefundFK = "fee_refund_fees_fee_refunds_fk"
    val FeesFK = "fee_refund_fees_fees_fk"
    val RefundedPaymentRHashCurrencyUnique = "r_hash_currency_unique"
  }

  def create(feeRefund: FeeRefund, refundedHashes: List[PaymentRHash])(implicit conn: Connection): Unit = {
    try {
      SQL"""
        INSERT INTO fee_refunds(fee_refund_id, currency, amount, status, requested_at, refunded_on)
        VALUES (
          ${feeRefund.id.uuid}::UUID,
          ${feeRefund.currency.entryName}::CURRENCY_TYPE,
          ${feeRefund.amount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${feeRefund.status.entryName}::REFUND_STATUS,
          ${feeRefund.requestedAt},
          ${feeRefund.refundedOn}
        )
        """
        .execute()
    } catch {
      case error: PSQLException if isDuplicatedRefundError(error) =>
        throw new PSQLException(
          s"Refund ${feeRefund.id} already exists.",
          PSQLState.DATA_ERROR
        )
    }

    refundedHashes.foreach { hash =>
      createFeeRefundFee(feeRefund.id, hash, feeRefund.currency)
    }
  }

  def find(paymentHash: PaymentRHash, currency: Currency)(implicit conn: Connection): Option[FeeRefund] = {
    val hash = paymentHash.value.toArray

    SQL"""
        SELECT
          fr.fee_refund_id, fr.currency, fr.amount, fr.status, fr.requested_at, fr.refunded_on
        FROM fee_refunds fr
        INNER JOIN fee_refund_fees frf USING(fee_refund_id)
        WHERE frf.r_hash = $hash AND frf.currency = ${currency.entryName}::CURRENCY_TYPE
     """.as(FeeRefundParsers.feeRefundParser.singleOpt)
  }

  def findForUpdate(id: FeeRefund.Id)(implicit conn: Connection): Option[FeeRefund] = {
    SQL"""
        SELECT
          fee_refund_id, currency, amount, status, requested_at, refunded_on
        FROM fee_refunds
        WHERE fee_refund_id = ${id.uuid}::UUID
        FOR UPDATE
     """.as(FeeRefundParsers.feeRefundParser.singleOpt)
  }

  def update(feeRefund: FeeRefund)(implicit connection: Connection): Unit = {
    SQL"""
        UPDATE 
          fee_refunds
        SET
          status = ${feeRefund.status.entryName}::REFUND_STATUS,
          refunded_on = ${feeRefund.refundedOn}
        WHERE
          fee_refund_id = ${feeRefund.id.uuid}::UUID
        """
      .execute()

    ()
  }

  private def createFeeRefundFee(
      refundId: FeeRefund.Id,
      refundedHash: PaymentRHash,
      currency: Currency
  )(implicit connection: Connection) = {
    try {
      val hash = refundedHash.value.toArray

      SQL"""
        INSERT INTO fee_refund_fees(fee_refund_id, r_hash, currency)
        VALUES (
          ${refundId.uuid}::UUID,
          $hash,
          ${currency.entryName}::CURRENCY_TYPE
        )
        """
        .execute()

      ()
    } catch {
      case error: PSQLException if isDuplicatedRefundedPaymentRHashError(error) =>
        throw new PSQLException(
          s"Refund for $refundedHash in $currency already exists.",
          PSQLState.DATA_ERROR
        )
      case error: PSQLException if isFeeRefundDoesNotExistsError(error) =>
        throw new PSQLException(
          s"Fee refund ${refundId} does not exists",
          PSQLState.DATA_ERROR
        )
      case error: PSQLException if isFeeDoesNotExistsError(error) =>
        throw new PSQLException(
          s"Fee for $refundedHash in $currency does not exist",
          PSQLState.DATA_ERROR
        )
    }
  }

  private def isDuplicatedRefundError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.FeeRefundPK
  }

  private def isDuplicatedRefundedPaymentRHashError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.RefundedPaymentRHashCurrencyUnique
  }

  private def isFeeRefundDoesNotExistsError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.FeeRefundFK
  }

  private def isFeeDoesNotExistsError(error: PSQLException): Boolean = {
    error.getServerErrorMessage.getConstraint == Constraints.FeesFK
  }
}
