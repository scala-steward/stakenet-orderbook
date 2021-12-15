package io.stakenet.orderbook.models.lnd

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.models.Currency.XSN
import io.stakenet.orderbook.models.Satoshis
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers._

class FeeSpec extends AnyWordSpec {
  "refundableFeeAmount " should {

    "get the correct amount" in {
      val paidAmount = Helpers.asSatoshis("0.000015")
      val fee1 = Fee(XSN, Helpers.randomPaymentHash(), paidAmount, None, Instant.now, BigDecimal(0.05))
      val expectedValue = Helpers.asSatoshis("0.00000075")
      fee1.refundableFeeAmount must be(expectedValue)

      val paidAmount2 = Helpers.asSatoshis("0.01")
      val fee2 = fee1.copy(paidAmount = paidAmount2, feePercent = BigDecimal(0.025))
      val expectedValue2 = Helpers.asSatoshis("0.00025")
      fee2.refundableFeeAmount must be(expectedValue2)
    }

    "get zero amount" in {
      val paidAmount = Helpers.asSatoshis("0.0000001")
      val fee1 = Fee(XSN, Helpers.randomPaymentHash(), paidAmount, None, Instant.now, BigDecimal(0.025))
      fee1.refundableFeeAmount must be(Satoshis.Zero)

      val paidAmount2 = Satoshis.Zero
      val fee2 = fee1.copy(paidAmount = paidAmount2)
      fee2.refundableFeeAmount must be(Satoshis.Zero)
    }

    "get an exception with incorrect values" in {
      val paidAmount = Helpers.asSatoshis("10000000000000000000000.0")

      intercept[Exception] {
        Fee(XSN, Helpers.randomPaymentHash(), paidAmount, None, Instant.now, BigDecimal(1555))
      }
    }
  }
}
