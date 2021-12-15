package io.stakenet.orderbook.models.trading

import helpers.Helpers
import io.stakenet.orderbook.models.Currency.{BTC, XSN}
import io.stakenet.orderbook.models.Satoshis
import org.scalatest.OptionValues._
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.matchers.must.Matchers._
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.util.Try

class SatoshisSpec extends AnyWordSpec with TimeLimitedTests {

  override def timeLimit: Span = 1.second

  "from BigInt" should {

    "construct a satoshis object with BigInt amount " in {
      val dataTest1 = List(
        (BigInt(1), BigDecimal("0.00000001")),
        (BigInt(0), BigDecimal("0.0")),
        (BigInt(15000), BigDecimal("0.00015")),
        (BigInt(54112369), BigDecimal("0.54112369")),
        (BigInt(178), BigDecimal("0.00000178")),
        (BigInt(98798654), BigDecimal("0.98798654")),
        (BigInt(654121), BigDecimal("0.00654121")),
        (BigInt(122254454), BigDecimal("1.22254454")),
        (BigInt(123456789123456L), BigDecimal("1234567.89123456"))
      )

      val dataTest2 = List(
        (BigInt(1), BigDecimal("0.0000000001")),
        (BigInt(0), BigDecimal("0.0")),
        (BigInt(15000), BigDecimal("0.0000015")),
        (BigInt(54112369), BigDecimal("0.0054112369")),
        (BigInt(178), BigDecimal("0.0000000178")),
        (BigInt(98798654), BigDecimal("0.0098798654")),
        (BigInt(654121), BigDecimal("0.0000654121")),
        (BigInt(122254454), BigDecimal("0.0122254454")),
        (BigInt(123456789123456L), BigDecimal("12345.6789123456"))
      )

      for ((value, expected) <- dataTest1) {
        val result = Satoshis.from(value, 8)
        result.value.toBigDecimal must be(expected)
      }

      for ((value, expected) <- dataTest2) {
        val result = Satoshis.from(value, 10)
        result.value.toBigDecimal must be(expected)
      }
    }

    "fail when the BigInt amount is bigger than maxValue" in {
      val dataTest = List(
        Satoshis.MaxValue.value(8) + 1,
        Satoshis.MaxValue.value(8) + 5,
        Satoshis.MaxValue.value(8) + 25,
        Satoshis.MaxValue.value(8) + 3000,
        Satoshis.MaxValue.value(8) + 45,
        Satoshis.MaxValue.value(8) + 7,
        Satoshis.MaxValue.value(8) + 80,
        Satoshis.MaxValue.value(8) + 12345678
      )

      for (value <- dataTest) {
        val result = Satoshis.from(value, 8)
        result must be(empty)
      }
    }

    "fail when the BigInt amount is negative" in {
      val dataTest = List(
        BigInt(-1),
        BigInt(-10),
        BigInt(-15000),
        BigInt(-54112369),
        BigInt(-178),
        BigInt(-98798654),
        BigInt(-654121),
        BigInt(-22254454)
      )

      for (value <- dataTest) {
        val result = Satoshis.from(value, 8)
        result must be(empty)
      }
    }
  }

  "from BigDecimal" should {
    "construct a satoshis object with Decimal amounts" in {
      val dataTest = List(
        (BigDecimal(1), BigInt(100000000)),
        (BigDecimal(0), BigInt(0)),
        (BigDecimal(15000), BigInt(1500000000000L)),
        (BigDecimal(0.54112369), BigInt(54112369)),
        (BigDecimal(0.178), BigInt(17800000)),
        (BigDecimal(0.5), BigInt(50000000)),
        (BigDecimal(52.36), BigInt(5236000000L)),
        (BigDecimal(789.12345678), BigInt(78912345678L))
      )

      for ((value, expected) <- dataTest) {
        val result = Satoshis.from(value)
        result.value.value(8) must be(expected)
      }
    }

    "fail when the Decimal amount becomes bigger than maxValue" in {
      val dataTest = List(
        Satoshis.MaxValue.toBigDecimal + 1,
        Satoshis.MaxValue.toBigDecimal + .5,
        Satoshis.MaxValue.toBigDecimal + 0.025,
        Satoshis.MaxValue.toBigDecimal + 3000,
        Satoshis.MaxValue.toBigDecimal + 45,
        Satoshis.MaxValue.toBigDecimal + BigDecimal("0.00000001"),
        Satoshis.MaxValue.toBigDecimal + 0.98653211,
        Satoshis.MaxValue.toBigDecimal + 12345678
      )

      for (value <- dataTest) {
        val result = Satoshis.from(value)
        result must be(empty)
      }
    }

    "fail when the Decimal amount is negative" in {
      val amount = BigDecimal(-500.20)
      val result = Satoshis.from(amount)
      result must be(empty)
    }

    "fail when the Decimal amount has more than 18 decimals" in {
      val dataTest = List(
        BigDecimal("21121.0000000000004123154564"),
        BigDecimal("654654.0000000000004123154564"),
        BigDecimal("54.0000000000004123154564"),
        BigDecimal("0.0000000000004123154564")
      )

      for (value <- dataTest) {
        intercept[Exception] {
          val _ = Satoshis.from(value)
        }
      }
    }

    "fails fast when the BigDecimal amount is huge" in {
      val input = BigDecimal("1e100000000")
      val result = Satoshis.from(input)
      result must be(empty)
    }
  }

  "+ operator" should {
    "sum two Satoshis objects" in {
      val a = Helpers.asSatoshis("0.000001")
      val b = Helpers.asSatoshis("0.00000025")

      val result = a + b
      val expected = Helpers.asSatoshis("0.00000125")

      result must be(expected)
    }

    "fail when sum exceeds maximum permitted value" in {
      val a = Satoshis.MaxValue
      val b = Helpers.asSatoshis("0.00000025")

      val result = Try(a + b)

      result.isFailure must be(true)
    }
  }

  "max" should {
    "return a when a is bigger than b" in {
      val a = Helpers.asSatoshis("0.000001")
      val b = Helpers.asSatoshis("0.00000025")

      val result = a.max(b)

      result must be(a)
    }

    "return b when b is bigger than a" in {
      val a = Helpers.asSatoshis("0.00000025")
      val b = Helpers.asSatoshis("0.000001")

      val result = a.max(b)

      result must be(b)
    }

    "work when a and b are equal" in {
      val a = Helpers.asSatoshis("0.000001")
      val b = Helpers.asSatoshis("0.000001")

      val result = a.max(b)

      result must be(a)
    }
  }

  "toReadableUSDValue" should {
    "return the correct value" in {
      val currency1 = XSN
      val currency2 = BTC
      val a = currency1.satoshis(BigInt(1000000000)).value
      val b = currency2.satoshis(BigInt(5000000)).value
      val price1 = BigDecimal(0.02546)
      val price2 = BigDecimal(9532)

      val result = a.toReadableUSDValue(currency1, price1)
      val result2 = b.toReadableUSDValue(currency2, price2)

      result must be("10.00000000 XSN (0.25 USD)")
      result2 must be("0.05000000 BTC (476.60 USD)")
    }
  }

  "*" should {
    "multiply by an int value" in {
      val value = Helpers.asSatoshis("0.00000123")

      val result = value * 2

      val expected = Helpers.asSatoshis("0.00000246")
      result mustBe expected
    }

    "fail when result exceeds max value" in {
      val value = Satoshis.MaxValue

      val result = Try(value * 2)

      result.isFailure mustBe true
    }

    "multiply by a decimal value" in {
      val value = Helpers.asSatoshis("0.00000123")

      val result = value * 1.2

      val expected = Helpers.asSatoshis("0.000001476")
      result mustBe expected
    }
  }

  "equals" should {
    "return true when values are equal" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456789")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.123456789")).value

      satoshis1.equals(satoshis2, digits = 18) mustBe true
    }

    "return false when values are not equal" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456789")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.123456780")).value

      satoshis1.equals(satoshis2, digits = 18) mustBe false
    }

    "ignore extra digits" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456789")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.12345678")).value

      satoshis1.equals(satoshis2, digits = 8) mustBe true
    }
  }

  "lessThan" should {
    "return true when value is less" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456788")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.123456789")).value

      satoshis1.lessThan(satoshis2, digits = 18) mustBe true
    }

    "return false when value is greater" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456789")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.123456788")).value

      satoshis1.lessThan(satoshis2, digits = 18) mustBe false
    }

    "return false when value is equal" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456789")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.123456789")).value

      satoshis1.lessThan(satoshis2, digits = 18) mustBe false
    }

    "ignore extra digits" in {
      val satoshis1 = Satoshis.from(BigDecimal("0.123456779")).value
      val satoshis2 = Satoshis.from(BigDecimal("0.12345678")).value

      satoshis1.lessThan(satoshis2, digits = 8) mustBe true
    }
  }
}
