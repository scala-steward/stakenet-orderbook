package io.stakenet.orderbook.models.lnd

import helpers.Helpers
import io.stakenet.orderbook.models.{Currency, Satoshis}
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class ChannelFeePaymentSpec extends AnyWordSpec {
  "calculate the exact opening fee" should {
    val capacity = Helpers.asSatoshis("0.0005")

    "useful for debugging" in {
      val capacity = Satoshis.from(49.99227883).value
      val rentingCurrency = Currency.XSN
      val payingCurrency = Currency.BTC
      val lifetime = 12.hours
      val expected = Helpers.asSatoshis("0.0279996761448")
      val feePayment =
        ChannelFeePayment(
          currency = rentingCurrency,
          payingCurrency = payingCurrency,
          capacity,
          lifetime.toSeconds,
          Satoshis.Zero
        )
      val actualFee = feePayment.fees.totalFee

      println(s"Amount to rent: ${capacity.toString(rentingCurrency)}")
      println(
        s"Fee: ${rentingCurrency.rentChannelFeePercentage}% * $lifetime = ${rentingCurrency.rentChannelFeePercentage * lifetime.toHours}"
      )
      println(s"Actual fee: ${actualFee.toString(payingCurrency)}")
      expected must be(actualFee)
    }

    "calculate the fee for a given price" in {
      val feePayment = ChannelFeePayment(Currency.XSN, Currency.BTC, capacity, 1.hour.toSeconds, Satoshis.Zero)
      val expectedFee = Helpers.asSatoshis("0.00000406")
      feePayment.fees.totalFee must be(expectedFee)
    }

    " calculate the fee for the inverse currencies" in {
      val feePayment2 = ChannelFeePayment(Currency.BTC, Currency.XSN, capacity, 1.hour.toSeconds, Satoshis.Zero)
      val expectedFee2 = Helpers.asSatoshis("0.00022762")
      feePayment2.fees.totalFee must be(expectedFee2)
    }

    "calculate the fee for two hours" in {
      val feePayment3 = ChannelFeePayment(Currency.LTC, Currency.BTC, capacity, 2.hours.toSeconds, Satoshis.Zero)
      val expectedFee3 = Helpers.asSatoshis("0.00000408")
      feePayment3.fees.totalFee must be(expectedFee3)
    }
  }

  "calculate the exact extension fee" should {
    val capacity = Helpers.asSatoshis("0.0005")

    "calculate the fee for a given price" in {
      val feePayment = ChannelFeePayment(Currency.XSN, Currency.BTC, capacity, 1.hour.toSeconds, Satoshis.Zero)
      val expectedFee = Helpers.asSatoshis("0.00000002")
      feePayment.fees.extensionFee must be(expectedFee)
    }

    " calculate the fee for the inverse currencies" in {
      val feePayment2 = ChannelFeePayment(Currency.BTC, Currency.XSN, capacity, 1.hour.toSeconds, Satoshis.Zero)
      val expectedFee2 = Helpers.asSatoshis("0.00000002")
      feePayment2.fees.extensionFee must be(expectedFee2)
    }

    "calculate the fee for two hours" in {
      val feePayment3 = ChannelFeePayment(Currency.LTC, Currency.BTC, capacity, 2.hours.toSeconds, Satoshis.Zero)
      val expectedFee3 = Helpers.asSatoshis("0.00000004")
      feePayment3.fees.extensionFee must be(expectedFee3)
      feePayment3.lifeTimeDays must be(BigDecimal(0.08))
    }

    "get one satoshi when the fee is zero" in {
      val capacity = Helpers.asSatoshis("0.00000001")
      val feePayment3 = ChannelFeePayment(Currency.XSN, Currency.BTC, capacity, 1, Satoshis.Zero)
      feePayment3.fees.extensionFee must be(Satoshis.One)
    }
  }
}
