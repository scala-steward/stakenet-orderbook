package io.stakenet.orderbook.models.lnd

import java.time.{Duration, Instant}

import io.stakenet.orderbook.helpers.SampleChannels
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._

class LndChannelSpec extends AnyWordSpec {
  "isExpired " should {
    "return true for an expired channel" in {
      val channel = SampleChannels.newChannel().copy(expiresAt = Some(Instant.now.minusSeconds(60)))

      channel.isExpired mustBe true
    }

    "return false for a non expired channel" in {
      val channel = SampleChannels.newChannel().copy(expiresAt = Some(Instant.now.plusSeconds(60)))

      channel.isExpired mustBe false
    }

    "return false for a channel without expiration date" in {
      val channel = SampleChannels.newChannel().copy(expiresAt = None)

      channel.isExpired mustBe false
    }
  }

  "remainingTime" should {
    "return remaining time for a non expired channel" in {
      val channel = SampleChannels.newChannel().copy(expiresAt = Some(Instant.now.plusSeconds(6000)))

      isCloseTo(channel.remainingTime.value, Duration.ofSeconds(6000)) mustBe true
    }

    "return remaining time for an expired channel" in {
      val channel = SampleChannels.newChannel().copy(expiresAt = Some(Instant.now.minusSeconds(6000)))

      isCloseTo(channel.remainingTime.value, Duration.ofSeconds(-6000)) mustBe true
    }

    "return None for a channel without expiration date" in {
      val channel = SampleChannels.newChannel().copy(expiresAt = None)

      channel.remainingTime mustBe None
    }
  }

  private def isCloseTo(duration1: Duration, duration2: Duration): Boolean = {
    duration1.minus(duration2).abs().toMillis <= 5
  }
}
