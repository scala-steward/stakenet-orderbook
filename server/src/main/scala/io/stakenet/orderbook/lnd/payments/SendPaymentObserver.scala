package io.stakenet.orderbook.lnd.payments

import io.grpc.stub.StreamObserver
import lnrpc.rpc.{Payment, PaymentFailureReason}
import lnrpc.rpc.Payment.PaymentStatus
import org.slf4j.LoggerFactory

class SendPaymentObserver(
    paymentRequest: String,
    onFailed: PaymentFailureReason => Unit,
    onSucceeded: () => Unit,
    onException: Throwable => Unit
) extends StreamObserver[Payment] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def onCompleted(): Unit = {
    logger.info(s"Payment failed for: $paymentRequest")
  }

  override def onError(exception: Throwable): Unit = {
    logger.warn(s"An error occurred on payment for: $paymentRequest", exception)
    onException(exception)
  }

  override def onNext(payment: Payment): Unit = {
    payment.status match {
      case PaymentStatus.SUCCEEDED =>
        onSucceeded()
      case PaymentStatus.FAILED =>
        logger.warn(s"Payment for $paymentRequest failed due to: ${payment.failureReason.name}")
        onFailed(payment.failureReason)
      case _ => ()
    }
  }
}
