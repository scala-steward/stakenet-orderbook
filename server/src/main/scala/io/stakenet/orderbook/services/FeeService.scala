package io.stakenet.orderbook.services

import java.time.Instant

import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd.{Fee, PaymentData, PaymentRHash, RefundablePayment}
import io.stakenet.orderbook.models.reports.PartialOrder
import io.stakenet.orderbook.models.trading.TradingOrder
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.services.FeeService.Errors.{
  CouldNotCalculateRefundableAmount,
  CouldNotRefundFee,
  CouldNotTakeFee
}

import scala.concurrent.Future

trait FeeService {
  def createInvoice(currency: Currency, amount: Satoshis): Future[Either[String, String]]
  def takeFee(clientId: ClientId, order: TradingOrder, hash: PaymentRHash): Future[Either[CouldNotTakeFee, Unit]]
  def unlink(orderId: OrderId): Future[Unit]
  def burn(orderId: OrderId, currency: Currency, amount: Satoshis): Future[Unit]
  def find(orderId: OrderId, currency: Currency): Future[Option[Fee]]
  def savePartialOrder(partialOrder: PartialOrder): Future[Unit]

  def refund(
      clientId: ClientId,
      currency: Currency,
      refundedFees: List[RefundablePayment]
  ): Future[Either[CouldNotRefundFee, (Satoshis, Instant)]]

  def getRefundableAmount(
      currency: Currency,
      paymentRHashList: List[RefundablePayment]
  ): Future[Either[CouldNotCalculateRefundableAmount, Satoshis]]

  def getPaymentData(
      clientId: ClientId,
      paymentCurrency: Currency,
      hash: PaymentRHash
  ): Future[Either[String, PaymentData]]
}

object FeeService {
  trait Error

  object Errors {
    final case class CouldNotTakeFee(reason: String) extends Error
    final case class CouldNotRefundFee(reason: String) extends Error
    final case class CouldNotCalculateRefundableAmount(reason: String) extends Error
  }
}
