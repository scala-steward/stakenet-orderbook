package io.stakenet.orderbook.actors.peers

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GetRefundableAmount
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{CommandFailed, GetRefundableAmountResponse}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleOrders.getSatoshis
import io.stakenet.orderbook.models.lnd.{Fee, FeeInvoice, RefundablePayment}
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.MockitoSugar.{mock, when}

import scala.concurrent.Future

class GetRefundableAmountSpec extends PeerSpecBase("getRefundableAmountSpec") {
  val feePercent = BigDecimal(0.0025)
  val refundablePayment = RefundablePayment(xsnRHash, Satoshis.One)
  val refundablePayment2 = RefundablePayment(xsnRHash2, Satoshis.One)
  val paidAt = Instant.now().minusSeconds(800000)

  "getRefundableAmount" should {
    "respond with getRefundableAmountResponse" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesRepository = feesRepository) { alice =>
        val requestId = "id"
        val fee = Fee(Currency.XSN, xsnRHash, getSatoshis(10000), None, paidAt, feePercent)
        val refundableAmount = fee.refundableFeeAmount

        when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee))

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, List(refundablePayment)))
        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some(requestId), GetRefundableAmountResponse(fee.currency, refundableAmount))
        )
      }
    }

    "get zero fee amount" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesRepository = feesRepository) { alice =>
        val requestId = "id"

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(
          Some(Fee(Currency.XSN, xsnRHash, Satoshis.Zero, None, paidAt, feePercent))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, List(refundablePayment)))
        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some(requestId), GetRefundableAmountResponse(Currency.XSN, Satoshis.Zero))
        )
      }
    }

    "return fee amount from two given elements" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesRepository = feesRepository) { alice =>
        val requestId = "id"

        val fees = List(
          Fee(Currency.XSN, xsnRHash, getSatoshis(10000), None, paidAt, feePercent),
          Fee(Currency.XSN, xsnRHash2, getSatoshis(50000), None, paidAt, feePercent)
        )

        fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))

        val refunds = List(refundablePayment, refundablePayment2)
        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, refunds))

        val feeExpected = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some(requestId), GetRefundableAmountResponse(Currency.XSN, feeExpected))
        )
      }
    }

    "fail for unknown elements" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(feesRepository = feesRepository, paymentService = paymentService) { alice =>
        val requestId = "id"

        val fee = Fee(Currency.XSN, xsnRHash, getSatoshis(10000), None, paidAt, feePercent)
        val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.paidAmount, Instant.now)
        when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee))
        when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
        when(feesRepository.find(xsnRHash2, Currency.XSN)).thenReturn(None)
        when(feesRepository.findInvoice(xsnRHash2, Currency.XSN)).thenReturn(None)
        when(paymentService.isPaymentComplete(Currency.XSN, xsnRHash2)).thenReturn(Future.successful(false))

        val refunds = List(refundablePayment, refundablePayment2)
        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, refunds))

        val error = s"[Fee with payment hash $xsnRHash2 not found]"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }

    "fail when the list is empty" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesRepository = feesRepository) { alice =>
        val requestId = "id"

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, List.empty))

        val error = "A list of payment hash was expected, but an empty list was provided"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }

    "fail when fee is locked for an order" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val orderId = OrderId.random()
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, Some(orderId), twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, refundedFees))

        val errorMessage = s"[Fee ${fee.paymentRHash} is locked for order $orderId]"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(errorMessage)))
      }
    }

    "report multiple errors" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val orderId = OrderId.random()
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("0.00000123")
        val fee1 = Fee(Currency.XSN, xsnRHash2, amount, Some(orderId), twentyYearsAgo, feePercent)
        val fee2 = Fee(Currency.XSN, xsnRHash, amount, None, Instant.now(), feePercent)
        val refundedFees = List(
          RefundablePayment(fee1.paymentRHash, Satoshis.Zero),
          RefundablePayment(fee2.paymentRHash, Satoshis.Zero)
        )

        when(feesRepository.find(fee1.paymentRHash, fee1.currency)).thenReturn(Some(fee1))
        when(feesRepository.find(fee2.paymentRHash, fee2.currency)).thenReturn(Some(fee2))
        when(feeRefundsRepository.find(xsnRHash2, fee1.currency)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, refundedFees))

        val error1 = s"Fee ${fee1.paymentRHash} is locked for order $orderId"
        val error2 = s"Fee ${fee2.paymentRHash} needs to wait 1 day from the fee payment for a refund"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"[$error1, $error2]")))
      }
    }

    "fail when the same fee is sent multiple times" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val refundedFees = List(
          RefundablePayment(xsnRHash, Satoshis.Zero),
          RefundablePayment(xsnRHash, Satoshis.Zero),
          RefundablePayment(xsnRHash, Satoshis.Zero),
          RefundablePayment(xsnRHash2, Satoshis.Zero),
          RefundablePayment(xsnRHash2, Satoshis.Zero)
        )

        when(feeRefundsRepository.find(xsnRHash, Currency.XSN)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, refundedFees))

        val error = s"[$xsnRHash was sent 3 times, $xsnRHash2 was sent 2 times]"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }

    "respond with getRefundableAmountResponse for an unused fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(feesRepository = feesRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val fee = Fee(Currency.XSN, xsnRHash, getSatoshis(10000), None, paidAt, feePercent)
        val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.refundableFeeAmount, Instant.now)
        val refundableAmount = fee.refundableFeeAmount

        when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(None)
        when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
        when(paymentService.isPaymentComplete(invoice.currency, invoice.paymentHash)).thenReturn(
          Future.successful(true)
        )

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, List(refundablePayment)))
        alice.client.expectMsg(
          WebSocketOutgoingMessage(1, Some(requestId), GetRefundableAmountResponse(fee.currency, refundableAmount))
        )
      }
    }

    "fail for an unused fee that is not paid" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      withSinglePeer(feesRepository = feesRepository, paymentService = paymentService) { alice =>
        val requestId = "id"
        val fee = Fee(Currency.XSN, xsnRHash, getSatoshis(10000), None, paidAt, feePercent)
        val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.refundableFeeAmount, Instant.now)

        when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(None)
        when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
        when(paymentService.isPaymentComplete(invoice.currency, invoice.paymentHash)).thenReturn(
          Future.successful(false)
        )

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.XSN, List(refundablePayment)))

        val error = s"[Fee with payment hash ${fee.paymentRHash} is not paid]"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }

    "fail for ETH" in {
      withSinglePeer() { alice =>
        val requestId = "id"

        alice.actor ! WebSocketIncomingMessage(requestId, GetRefundableAmount(Currency.ETH, List(refundablePayment)))

        val error = "ETH not supported"
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
      }
    }
  }
}
