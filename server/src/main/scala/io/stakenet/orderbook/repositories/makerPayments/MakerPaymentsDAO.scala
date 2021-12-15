package io.stakenet.orderbook.repositories.makerPayments

import java.sql.Connection

import anorm._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.Trade
import io.stakenet.orderbook.models.{Currency, MakerPayment, MakerPaymentId, MakerPaymentStatus, Satoshis}
import org.postgresql.util.{PSQLException, PSQLState}

private[makerPayments] object MakerPaymentsDAO {

  object Constraints {
    val makerPaymentPK = "maker_payment_pk"
    val makerPaymentClientFK = "maker_payment_client_fk"
    val makerPaymentTradeFK = "maker_payment_trade_fk"
  }

  def createMakerPayment(
      makerPaymentId: MakerPaymentId,
      tradeId: Trade.Id,
      clientId: ClientId,
      amount: Satoshis,
      currency: Currency,
      status: MakerPaymentStatus
  )(implicit conn: Connection): Unit = {
    try {
      val _ = SQL"""
        INSERT INTO maker_payments(maker_payment_id, trade_id, client_id, amount, currency, status)
        VALUES (
          ${makerPaymentId.value}::UUID,
          ${tradeId.value}::UUID,
          ${clientId.toString}::UUID,
          ${amount.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${currency.entryName}::CURRENCY_TYPE,
          ${status.entryName}::MAKER_PAYMENT_STATUS_TYPE
        )
        """
        .execute()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.makerPaymentPK) =>
        throw new PSQLException(s"maker payment $makerPaymentId already exist", PSQLState.DATA_ERROR)

      case e: PSQLException if violatesConstraint(e, Constraints.makerPaymentClientFK) =>
        throw new PSQLException(s"client $clientId not found", PSQLState.DATA_ERROR)

      case e: PSQLException if violatesConstraint(e, Constraints.makerPaymentTradeFK) =>
        throw new PSQLException(s"trade $tradeId not found", PSQLState.DATA_ERROR)
    }
  }

  def getFailedPayments(clientId: ClientId)(implicit conn: Connection): List[MakerPayment] = {
    val failedStatus = MakerPaymentStatus.Failed

    SQL"""
        SELECT maker_payment_id, trade_id, client_id, amount, currency, status
        FROM maker_payments
        WHERE status = ${failedStatus.entryName}::MAKER_PAYMENT_STATUS_TYPE
          AND client_id = ${clientId.toString}::UUID
        ORDER BY maker_payment_id
     """.as(MakerPaymentsParsers.makerPaymentParser.*)
  }

  def updateStatus(makerPaymentId: MakerPaymentId, status: MakerPaymentStatus)(implicit conn: Connection): Unit = {
    SQL"""
        UPDATE maker_payments
        SET status = ${status.entryName}::MAKER_PAYMENT_STATUS_TYPE
        WHERE maker_payment_id = ${makerPaymentId.toString}::UUID
     """.executeUpdate()

    ()
  }

  private def violatesConstraint(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
