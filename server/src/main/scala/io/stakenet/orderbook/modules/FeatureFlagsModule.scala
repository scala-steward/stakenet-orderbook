package io.stakenet.orderbook.modules

import com.google.inject.{AbstractModule, Provides}
import io.stakenet.orderbook.config.FeatureFlags
import org.slf4j.LoggerFactory
import play.api.Configuration

class FeatureFlagsModule extends AbstractModule {

  private val logger = LoggerFactory.getLogger(this.getClass)

  @Provides
  def build(config: Configuration): FeatureFlags = {
    val result = FeatureFlags(config.get[Configuration]("featureFlags"))
    logger.info(
      s"Loading Feature Flags, feesEnabled = ${result.feesEnabled}, rejectingCountries = ${result.rejectBlacklistedCountries}"
    )
    result
  }
}
