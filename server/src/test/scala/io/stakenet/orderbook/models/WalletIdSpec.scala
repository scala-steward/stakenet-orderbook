package io.stakenet.orderbook.models

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues._

class WalletIdSpec extends AnyWordSpec with Matchers {
  "apply" should {
    "return Some for a valid id" in {
      val id = "048d669299fba67ddbbcfa86fb3a344d0d3a5066"

      val result = WalletId(id).value

      result.toString mustBe id
    }

    "return None for an id with an extra character" in {
      val id = "048d669299fba67ddbbcfa86fb3a344d0d3a5066a"

      val result = WalletId(id)

      result mustBe None
    }

    "return None for an id with a missing character" in {
      val id = "048d669299fba67ddbbcfa86fb3a344d0d3a506"

      val result = WalletId(id)

      result mustBe None
    }

    "return None for an id with an uppercase letter" in {
      val id = "048d669299fba67ddbbcfa86fb3a344d0d3a506A"

      val result = WalletId(id)

      result mustBe None
    }

    "return None for an id with a special character" in {
      val id = "048d669299fba67ddbbcfa86fb3a344d0-3a5066"

      val result = WalletId(id)

      result mustBe None
    }
  }
}
