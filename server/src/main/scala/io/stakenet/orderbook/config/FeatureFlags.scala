package io.stakenet.orderbook.config

import play.api.Configuration

case class FeatureFlags(feesEnabled: Boolean, rejectBlacklistedCountries: Boolean)

object FeatureFlags {

  def apply(config: Configuration): FeatureFlags = {
    val feesEnabled = config.get[Boolean]("feesEnabled")
    val rejectBlacklistedCountries = config.get[Boolean]("rejectBlacklistedCountries")

    FeatureFlags(
      feesEnabled = feesEnabled,
      rejectBlacklistedCountries = rejectBlacklistedCountries
    )
  }
}
