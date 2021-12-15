package io.stakenet.orderbook.repositories.fees

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.models.lnd.{Fee, PaymentRHash}
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.fees.requests.{BurnFeeRequest, LinkFeeToOrderRequest}
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

class FeesPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new FeesPostgresRepository(database)

  "linkOrder" should {
    lazy val paymentHash =
      PaymentRHash.untrusted("a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6").value
    val currency = Currency.XSN
    val orderId = OrderId.random()
    lazy val request = LinkFeeToOrderRequest(
      hash = paymentHash,
      currency = currency,
      amount = Helpers.asSatoshis("0.000001"),
      orderId = orderId,
      Instant.now(),
      feePercent = 0.0025
    )

    "create a new fee when the hash doesn't exists" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      val stored = unsafeFind(paymentHash, currency)
      stored.lockedForOrderId must contain(orderId)
    }

    "lock the fee to a given order" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      unsafeFree(paymentHash, currency)

      val stored = unsafeFind(paymentHash, currency)
      stored.lockedForOrderId must be(empty)

      repository.linkOrder(request)
      val result = unsafeFind(paymentHash, currency)
      result.lockedForOrderId must contain(orderId)
    }

    "fail when the fee is already locked by another order" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      intercept[RuntimeException] {
        repository.linkOrder(request.copy(orderId = OrderId.random()))
      }
    }

    "fail when the fee is not locked but the amount is smaller than the requested one" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      unsafeFree(paymentHash, currency)
      intercept[RuntimeException] {
        repository.linkOrder(request.copy(amount = Helpers.asSatoshis("0.00000101")))
      }
    }
  }

  "unlink" should {
    lazy val paymentHash =
      PaymentRHash.untrusted("a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6").value
    val currency = Currency.XSN
    val orderId = OrderId.random()
    lazy val request = LinkFeeToOrderRequest(
      hash = paymentHash,
      currency = currency,
      amount = Helpers.asSatoshis("0.000001"),
      orderId = orderId,
      Instant.now(),
      feePercent = 0.0025
    )

    "remove the order id from the fee" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      repository.unlink(orderId)
      val result = unsafeFind(paymentHash, currency)
      result.lockedForOrderId must be(empty)
    }

    "do nothing if the fee doesn't exists" in {
      repository.unlink(orderId)
      succeed
    }
  }

  "burn" should {
    lazy val paymentHash =
      PaymentRHash.untrusted("a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6").value
    val currency = Currency.XSN
    val orderId = OrderId.random()
    lazy val request = LinkFeeToOrderRequest(
      hash = paymentHash,
      currency = currency,
      amount = Helpers.asSatoshis("0.000001"),
      orderId = orderId,
      Instant.now(),
      feePercent = 0.0025
    )

    "burning coins unlink the order" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      repository.burn(BurnFeeRequest(orderId, currency, Helpers.asSatoshis("0.0000001")))
      val result = unsafeFind(paymentHash, currency)
      result.lockedForOrderId must be(empty)
    }

    "burning coins reduce the amount" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      repository.burn(BurnFeeRequest(orderId, currency, Helpers.asSatoshis("0.0000001")))
      val result = unsafeFind(paymentHash, currency)
      val expected = Helpers.asSatoshis("0.0000009")
      result.paidAmount must be(expected)
    }

    "burning coins up to zero" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      repository.burn(BurnFeeRequest(orderId, currency, request.amount))
      val result = unsafeFind(paymentHash, currency)
      result.paidAmount must be(Satoshis.Zero)
    }

    "fail when the fee doesn't exists" in {
      intercept[RuntimeException] {
        repository.burn(BurnFeeRequest(orderId, currency, request.amount))
      }
    }

    "fail when the fee can't cover the amount to burn" in {
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)
      intercept[RuntimeException] {
        repository.burn(BurnFeeRequest(orderId, currency, Helpers.asSatoshis("0.00000101")))
      }
    }
  }

  "find" should {
    "find by order id" in {
      val request = randomLinkFeeToOrderRequest()
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)

      val result = repository.find(request.orderId, request.currency)
      result mustNot be(empty)
    }

    "get None when order id does not exists" in {
      val request = randomLinkFeeToOrderRequest()

      val result = repository.find(request.orderId, request.currency)
      result must be(empty)
    }

    "find by payment hash" in {
      val request = randomLinkFeeToOrderRequest()
      repository.createInvoice(request.hash, request.currency, request.amount)
      repository.linkOrder(request)

      val result = repository.find(request.hash, request.currency)
      result mustNot be(empty)
    }

    "get None when payment hash does not exists" in {
      val request = randomLinkFeeToOrderRequest()

      val result = repository.find(request.hash, request.currency)
      result mustBe empty
    }
  }

  "unlinkAll" should {
    "unlink all fees" in {
      val fees = List.fill(10)(randomLinkFeeToOrderRequest())
      fees.foreach { request =>
        repository.createInvoice(request.hash, request.currency, request.amount)
        repository.linkOrder(request)
      }

      repository.unlinkAll()

      fees.map(fee => unsafeFind(fee.hash, fee.currency).lockedForOrderId must be(empty))
    }

    "succeed when there are no fees" in {
      repository.unlinkAll()

      succeed
    }
  }

  "createInvoice" should {
    "succeed" in {
      repository.createInvoice(Helpers.randomPaymentHash(), Currency.XSN, Satoshis.One)

      succeed
    }

    "succeed for the same hash on a different currency" in {
      val hash = Helpers.randomPaymentHash()

      repository.createInvoice(hash, Currency.XSN, Satoshis.One)
      repository.createInvoice(hash, Currency.BTC, Satoshis.One)

      succeed
    }

    "fail when trying to store the same invoice twice" in {
      val hash = Helpers.randomPaymentHash()

      repository.createInvoice(hash, Currency.XSN, Satoshis.One)

      val error = intercept[PSQLException] {
        repository.createInvoice(hash, Currency.XSN, Satoshis.One)
      }

      error.getMessage mustBe "invalid paymentHash"
    }
  }

  private def unsafeFind(hash: PaymentRHash, currency: Currency): Fee = {
    val maybe = database.withConnection { implicit conn =>
      FeesDAO.find(hash, currency)
    }
    maybe.value
  }

  private def unsafeFree(hash: PaymentRHash, currency: Currency): Unit = {
    database.withConnection { implicit conn =>
      FeesDAO.free(hash, currency)
    }
  }

  private def randomLinkFeeToOrderRequest(): LinkFeeToOrderRequest = {
    val paymentHash = Helpers.randomPaymentHash()
    val currency = Currency.XSN
    val orderId = OrderId.random()

    LinkFeeToOrderRequest(
      hash = paymentHash,
      currency = currency,
      amount = Helpers.asSatoshis("0.000001"),
      orderId = orderId,
      Instant.now(),
      feePercent = 0.0025
    )
  }
}
