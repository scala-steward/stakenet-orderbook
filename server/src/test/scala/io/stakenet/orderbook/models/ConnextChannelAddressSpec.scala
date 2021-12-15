package io.stakenet.orderbook.models

import io.stakenet.orderbook.models.ChannelIdentifier.ConnextChannelAddress
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class ConnextChannelAddressSpec extends AnyWordSpec {
  "untrusted " should {
    "work for a valid value" in {
      val address = "0xB8b06869A32976641a41E75beBF647a1B5F05C9e"
      val result = ConnextChannelAddress(address)

      result.value.toString mustBe address
    }

    "fail for a string that is too long" in {
      val address = "0xB8b06869A32976641a41E75beBF647a1B5F05C9ea"
      val result = ConnextChannelAddress(address)

      result mustBe None
    }

    "fail for a string that does not start with 0x" in {
      val address = "abB8b06869A32976641a41E75beBF647a1B5F05C9e"
      val result = ConnextChannelAddress(address)

      result mustBe None
    }
  }
}
