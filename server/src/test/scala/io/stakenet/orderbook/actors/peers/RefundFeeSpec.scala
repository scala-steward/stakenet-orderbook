package io.stakenet.orderbook.actors.peers

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.RefundFee
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{CommandFailed, RefundFeeResponse}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.config.OrderFeesConfig
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito
import org.mockito.MockitoSugar.{mock, verify, when}
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration._

class RefundFeeSpec extends PeerSpecBase("RefundFeeSpec") {
  val feePercent = BigDecimal(0.0025)
  val orderFeesConfig = OrderFeesConfig(1.day)

  "RefundFee" should {
    "refund fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))
        when(paymentService.keySend(clientPublicKey.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
          Future.successful(Right(()))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        nextMsg(alice) match {
          case WebSocketOutgoingMessage(1, Some("id"), response: RefundFeeResponse) =>
            response.amount mustBe fee.refundableFeeAmount
            response.currency mustBe Currency.XSN
            response.refundedFees mustBe refundedFees
          case a => fail(a.toString)
        }
      }
    }

    "fail when storing refund on the database fails" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(
          feeRefundsRepository.createRefund(refundedFees.map(_.paymentRHash), fee.currency, fee.refundableFeeAmount)
        ).thenThrow(new RuntimeException("Timeout"))

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("Timeout"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when payment fails" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))

        when(paymentService.keySend(clientPublicKey.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
          Future.successful(Left(PaymentService.Error.PaymentFailed("rip")))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed("rip"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when fee does not exists" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(None)
        when(feeRefundsRepository.find(xsnRHash, Currency.XSN)).thenReturn(None)
        when(feesRepository.findInvoice(xsnRHash, Currency.XSN)).thenReturn(None)
        when(paymentService.isPaymentComplete(Currency.XSN, xsnRHash)).thenReturn(
          Future.successful(false)
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val error = s"[Fee with payment hash $xsnRHash not found]"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "fail when fee is locked for an order" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
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

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val errorMessage = s"[Fee ${fee.paymentRHash} is locked for order $orderId]"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(errorMessage))
        alice.client.expectMsg(expected)
      }
    }
    "fail when fee has not spent enough time in the hub" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val fee = Fee(Currency.XSN, xsnRHash, Helpers.asSatoshis("0.00000123"), None, Instant.now(), feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val error = s"[Fee ${fee.paymentRHash} needs to wait 1 day from the fee payment for a refund]"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "Fail when refund is already being processed" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val refund = FeeRefund(
          FeeRefund.Id.random(),
          fee.currency,
          fee.refundableFeeAmount,
          RefundStatus.Processing,
          Instant.now(),
          None
        )

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(Some(refund))

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val errorMessage = s"Refund is already in process"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(errorMessage))
        alice.client.expectMsg(expected)
      }
    }

    "Retry failed refund" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()
        val refund = FeeRefund(
          FeeRefund.Id.random(),
          fee.currency,
          fee.refundableFeeAmount,
          RefundStatus.Failed,
          Instant.now(),
          None
        )

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(Some(refund))
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))
        when(paymentService.keySend(clientPublicKey.key, refund.amount, Currency.XSN)).thenReturn(
          Future.successful(Right(()))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        nextMsg(alice) match {
          case WebSocketOutgoingMessage(1, Some("id"), response: RefundFeeResponse) =>
            response.amount mustBe fee.refundableFeeAmount
            response.currency mustBe Currency.XSN
            response.refundedFees mustBe refundedFees
          case _ => fail()
        }
      }
    }

    "Return refund data when refund has already been made" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val refund = FeeRefund(
          FeeRefund.Id.random(),
          fee.currency,
          fee.refundableFeeAmount,
          RefundStatus.Refunded,
          Instant.now,
          Some(Instant.now)
        )

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(Some(refund))

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val expected = RefundFeeResponse(fee.currency, fee.refundableFeeAmount, refundedFees, refund.refundedOn.value)
        alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), expected))
      }
    }

    "create refund" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))
        when(paymentService.keySend(clientPublicKey.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
          Future.successful(Right(()))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        verify(feeRefundsRepository, Mockito.timeout(1000)).createRefund(
          refundedFees.map(_.paymentRHash),
          fee.currency,
          fee.refundableFeeAmount
        )
      }
    }

    "Complete refund" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()
        val id = FeeRefund.Id.random()

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(feeRefundsRepository.createRefund(List(xsnRHash), Currency.XSN, fee.refundableFeeAmount)).thenReturn(id)
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))
        when(paymentService.keySend(clientPublicKey.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
          Future.successful(Right(()))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        verify(feeRefundsRepository, Mockito.timeout(1000)).completeRefund(id)
      }
    }

    "Fail refund" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()
        val id = FeeRefund.Id.random()

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(Some(fee))
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(feeRefundsRepository.createRefund(List(xsnRHash), Currency.XSN, fee.refundableFeeAmount)).thenReturn(id)
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))
        when(paymentService.keySend(clientPublicKey.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
          Future.successful(Left(PaymentService.Error.PaymentFailed("rip")))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        verify(feeRefundsRepository, Mockito.timeout(1000)).failRefund(id)
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

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val error1 = s"Fee ${fee1.paymentRHash} is locked for order $orderId"
        val error2 = s"Fee ${fee2.paymentRHash} needs to wait 1 day from the fee payment for a refund"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"[$error1, $error2]"))
        alice.client.expectMsg(expected)
      }
    }

    "fail when the same fee is sent multiple times" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
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

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val error = s"[$xsnRHash was sent 3 times, $xsnRHash2 was sent 2 times]"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }

    "refund unused fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository = feeRefundsRepository,
        clientsRepository = clientsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.refundableFeeAmount, Instant.now)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))
        val clientPublicKey = Helpers.randomClientPublicKey()

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(None)
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
        when(clientsRepository.findPublicKey(any[ClientId], any[Currency])).thenReturn(Some(clientPublicKey))
        when(paymentService.isPaymentComplete(invoice.currency, invoice.paymentHash)).thenReturn(
          Future.successful(true)
        )

        when(paymentService.keySend(clientPublicKey.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
          Future.successful(Right(()))
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        nextMsg(alice) match {
          case WebSocketOutgoingMessage(1, Some("id"), response: RefundFeeResponse) =>
            response.amount mustBe fee.refundableFeeAmount
            response.currency mustBe Currency.XSN
            response.refundedFees mustBe refundedFees
          case _ => fail()
        }
      }
    }

    "fail for unused fee that its not paid" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      withSinglePeer(
        feesRepository = feesRepository,
        paymentService = paymentService,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository = feeRefundsRepository
      ) { alice =>
        val requestId = "id"
        val twentyYearsAgo = Instant.ofEpochSecond(956021903)
        val amount = Helpers.asSatoshis("1.23456789")
        val fee = Fee(Currency.XSN, xsnRHash, amount, None, twentyYearsAgo, feePercent)
        val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.refundableFeeAmount, Instant.now)
        val refundedFees = List(RefundablePayment(xsnRHash, Satoshis.Zero))

        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(None)
        when(feeRefundsRepository.find(xsnRHash, fee.currency)).thenReturn(None)
        when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
        when(paymentService.isPaymentComplete(invoice.currency, invoice.paymentHash)).thenReturn(
          Future.successful(false)
        )

        alice.actor ! WebSocketIncomingMessage(requestId, RefundFee(Currency.XSN, refundedFees))

        val error = s"[Fee with payment hash ${fee.paymentRHash} is not paid]"
        val expected = WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error))
        alice.client.expectMsg(expected)
      }
    }
  }
}
