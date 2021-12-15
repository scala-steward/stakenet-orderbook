package io.stakenet.orderbook.config

import play.api.Configuration
import scala.concurrent.duration.FiniteDuration

case class RetryConfig(initialDelay: FiniteDuration, maxDelay: FiniteDuration)

object RetryConfig {

  def apply(config: Configuration): RetryConfig = {
    val initialDelay = config.get[FiniteDuration]("initialDelay")
    val maxDelay = config.get[FiniteDuration]("maxDelay")

    RetryConfig(initialDelay, maxDelay)
  }
}
