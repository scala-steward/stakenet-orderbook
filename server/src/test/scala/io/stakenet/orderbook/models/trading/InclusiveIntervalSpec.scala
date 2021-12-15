package io.stakenet.orderbook.models.trading

import helpers.Helpers
import io.stakenet.orderbook.models.Satoshis
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class InclusiveIntervalSpec extends AnyWordSpec {
  val ONE_SATOSHI = Satoshis.One
  val interval = Satoshis.InclusiveInterval(Helpers.asSatoshis("0.0000002"), Helpers.asSatoshis("0.0000005"))

  "contains" should {
    "return true for the lower bound of the interval" in {
      val result = interval.contains(interval.to)

      result must be(true)
    }

    "return true for the upper bound of the interval" in {
      val result = interval.contains(interval.from)

      result must be(true)
    }

    "return true for a number inside the interval" in {
      val result = interval.contains(interval.from + ONE_SATOSHI)

      result must be(true)
    }

    "return false for a number lower than the interval permits" in {
      val result = interval.contains(interval.from - ONE_SATOSHI)

      result must be(false)
    }

    "return false for a number higher than the interval permits" in {
      val result = interval.contains(interval.to + ONE_SATOSHI)

      result must be(false)
    }
  }
}
