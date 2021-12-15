package io.stakenet.orderbook.repositories.feeRefunds

import java.time.Instant

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.models.lnd.{Fee, PaymentRHash, RefundStatus}
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository.Errors.{CantCompleteRefund, CantFailRefund}
import io.stakenet.orderbook.repositories.fees.FeesPostgresRepository
import io.stakenet.orderbook.repositories.fees.requests.LinkFeeToOrderRequest
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

class FeeRefundsPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new FeeRefundsPostgresRepository(database)

  "refundFee" should {
    "create a refund" in {
      val fee = createFee(Currency.XSN, "0.000001")
      repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)

      val result = repository.find(fee.paymentRHash, fee.currency).value
      result.currency mustBe fee.currency
      result.amount mustBe fee.refundableFeeAmount
      result.status mustBe RefundStatus.Processing
      result.refundedOn mustBe None
    }

    "refund a fee with the same refunded payment r hash for a different currency" in {
      val refundedPaymentHash = randomPaymentHash()
      val feeXSN = createFee(Currency.XSN, "0.000001", refundedPaymentHash)
      val feeBTC = createFee(Currency.BTC, "0.000005", refundedPaymentHash)
      repository.createRefund(List(feeXSN.paymentRHash), feeXSN.currency, feeXSN.refundableFeeAmount)
      repository.createRefund(List(feeBTC.paymentRHash), feeBTC.currency, feeBTC.refundableFeeAmount)

      succeed
    }

    "set fees amount to zero" in {
      val fee = createFee(Currency.XSN, "0.000005")
      val refundedFees = List(
        createFee(Currency.XSN, "0.000001"),
        createFee(Currency.XSN, "0.000002"),
        createFee(Currency.XSN, "0.000003")
      )

      val refundedAmount = refundedFees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      repository.createRefund(refundedFees.map(_.paymentRHash), Currency.XSN, refundedAmount)

      val feeRepository = new FeesPostgresRepository(database)
      val nonRefundedFee = feeRepository.find(fee.paymentRHash, fee.currency).value
      nonRefundedFee.paidAmount mustBe fee.paidAmount

      refundedFees.map { refundedFee =>
        val fee = feeRepository.find(refundedFee.paymentRHash, refundedFee.currency).value

        fee.paidAmount mustBe Satoshis.Zero
      }
    }

    "unlink order from fee" in {
      val fee = createFee(Currency.XSN, "0.000005")
      val refundedFees = List(
        createFee(Currency.XSN, "0.000001"),
        createFee(Currency.XSN, "0.000002"),
        createFee(Currency.XSN, "0.000003")
      )

      val refundedAmount = refundedFees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      repository.createRefund(refundedFees.map(_.paymentRHash), Currency.XSN, refundedAmount)

      val feeRepository = new FeesPostgresRepository(database)
      val nonRefundedFee = feeRepository.find(fee.paymentRHash, fee.currency).value
      nonRefundedFee.lockedForOrderId mustBe fee.lockedForOrderId

      refundedFees.map { refundedFee =>
        val fee = feeRepository.find(refundedFee.paymentRHash, refundedFee.currency).value

        fee.lockedForOrderId mustBe None
      }
    }

    "fail when refunded fee does not exist" in {
      val paymentHash = randomPaymentHash()
      val error = intercept[PSQLException] {
        repository.createRefund(List(paymentHash), Currency.XSN, Satoshis.One)
      }

      error.getMessage mustBe s"Fee for $paymentHash in XSN does not exist"
    }

    "fail with duplicated refunded payment hash and currency" in {
      val fee = createFee(Currency.XSN, "0.000001")
      repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)

      val error = intercept[PSQLException] {
        repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)
      }

      error.getMessage mustBe s"Refund for ${fee.paymentRHash} in XSN already exists."
    }

    "fail when one of the fees does not exist" in {
      val refundedFees = List(
        createFee(Currency.XSN, "0.000001"),
        createFee(Currency.XSN, "0.000002"),
        createFee(Currency.XSN, "0.000003")
      )

      val nonExistentFeeHash = randomPaymentHash()
      val error = intercept[PSQLException] {
        val refundedAmount = refundedFees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
        val refundedHashes = nonExistentFeeHash :: refundedFees.map(_.paymentRHash)

        repository.createRefund(refundedHashes, Currency.XSN, refundedAmount)
      }

      error.getMessage mustBe s"Fee for $nonExistentFeeHash in ${Currency.XSN} does not exist"
    }
  }

  "find" should {
    "find refund by payment hash and currency" in {
      val feeXSN = createFee(Currency.XSN, "0.000001")
      val feeBTC = createFee(Currency.BTC, "0.000005")
      val feeLTC = createFee(Currency.LTC, "0.0000025")
      repository.createRefund(List(feeXSN.paymentRHash), feeXSN.currency, feeXSN.refundableFeeAmount)
      repository.createRefund(List(feeBTC.paymentRHash), feeBTC.currency, feeBTC.refundableFeeAmount)
      repository.createRefund(List(feeLTC.paymentRHash), feeLTC.currency, feeLTC.refundableFeeAmount)

      val result = repository.find(feeBTC.paymentRHash, Currency.BTC).value
      result.currency mustBe feeBTC.currency
      result.amount mustBe feeBTC.refundableFeeAmount
      result.status mustBe RefundStatus.Processing
      result.refundedOn mustBe None
    }

    "get None when payment request exists for another currency" in {
      val fee = createFee(Currency.XSN, "0.000001")
      repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)

      val result = repository.find(fee.paymentRHash, Currency.BTC)
      result mustBe None
    }

    "get None when refund does not exists" in {
      val result = repository.find(Helpers.randomPaymentHash(), Currency.XSN)
      result mustBe empty
    }
  }

  "completeRefund" should {
    "Update the status to refunded and set the refundedOn date" in {
      val fee = createFee(Currency.XSN, "0.000001")
      val id = repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)

      val result = repository.completeRefund(id)
      result mustBe Right(())

      val UpdatedRefund = repository.find(fee.paymentRHash, fee.currency).value
      UpdatedRefund.currency mustBe fee.currency
      UpdatedRefund.amount mustBe fee.refundableFeeAmount
      UpdatedRefund.status mustBe RefundStatus.Refunded
      UpdatedRefund.refundedOn mustNot equal(None)
    }

    "Fail if refund is already completed" in {
      val fee = createFee(Currency.XSN, "0.000001")
      val id = repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)
      repository.completeRefund(id)

      val result = repository.completeRefund(id)
      result mustBe Left(CantCompleteRefund(s"Refund $id was already in REFUNDED status."))
    }

    "Update the status to refunded and set the refundedOn date of a failed refund" in {
      val fee = createFee(Currency.XSN, "0.000001")
      val id = repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)
      repository.failRefund(id)

      repository.completeRefund(id)

      val UpdatedRefund = repository.find(fee.paymentRHash, fee.currency).value
      UpdatedRefund.currency mustBe fee.currency
      UpdatedRefund.amount mustBe fee.refundableFeeAmount
      UpdatedRefund.status mustBe RefundStatus.Refunded
      UpdatedRefund.refundedOn mustNot equal(None)
    }
  }

  "failRefund" should {
    "Update the status to failed" in {
      val fee = createFee(Currency.XSN, "0.000001")
      val id = repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)

      val result = repository.failRefund(id)
      result mustBe Right(())

      val updatedRefund = repository.find(fee.paymentRHash, fee.currency).value
      updatedRefund.currency mustBe fee.currency
      updatedRefund.amount mustBe fee.refundableFeeAmount
      updatedRefund.status mustBe RefundStatus.Failed
      updatedRefund.refundedOn mustBe None
    }

    "Fail if refund is already failed" in {
      val fee = createFee(Currency.XSN, "0.000001")
      val id = repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)
      repository.failRefund(id)

      val result = repository.failRefund(id)
      result mustBe Left(CantFailRefund(s"Refund $id was already in FAILED status."))
    }

    "Fail if refund is already completed" in {
      val fee = createFee(Currency.XSN, "0.000001")
      val id = repository.createRefund(List(fee.paymentRHash), fee.currency, fee.refundableFeeAmount)
      repository.completeRefund(id)

      val result = repository.failRefund(id)
      result mustBe Left(CantFailRefund(s"Refund $id was already in REFUNDED status."))
    }
  }

  private def createFee(currency: Currency, amount: String, paymentRHash: PaymentRHash = randomPaymentHash()): Fee = {
    val feeRepository = new FeesPostgresRepository(database)
    val request = LinkFeeToOrderRequest(
      hash = paymentRHash,
      currency = currency,
      amount = Helpers.asSatoshis(amount),
      orderId = OrderId.random(),
      Instant.now(),
      BigDecimal(.025)
    )

    feeRepository.createInvoice(request.hash, request.currency, request.amount)
    feeRepository.linkOrder(request)
    feeRepository.find(request.orderId, request.currency).value
  }
}
