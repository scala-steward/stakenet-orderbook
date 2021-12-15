package io.stakenet.orderbook.modules

import com.google.inject.{AbstractModule, Provides}
import io.stakenet.orderbook.config.{
  ChannelRentalConfig,
  DiscordConfig,
  ETHConfig,
  ExplorerConfig,
  IpInfoConfig,
  OrderFeesConfig,
  RetryConfig,
  TradesConfig,
  TradingPairsConfig
}
import org.slf4j.LoggerFactory
import play.api.Configuration

class ConfigModule extends AbstractModule {

  private val logger = LoggerFactory.getLogger(this.getClass)

  @Provides
  def orderFeesConfig(config: Configuration): OrderFeesConfig = {
    val result = OrderFeesConfig(config.get[Configuration]("orderFees"))
    logger.info(
      s"Loading OrderFeesConfig, refundableAfter = ${result.refundableAfter}"
    )
    result
  }

  @Provides
  def tradesConfig(config: Configuration): TradesConfig = {
    val tradesConfig = TradesConfig(config.get[Configuration]("trades"))
    logger.info(s"Loading TradesConfig, swapTimeout = ${tradesConfig.swapTimeout}")

    tradesConfig
  }

  @Provides
  def tradingPairConfig(global: Configuration): TradingPairsConfig = {
    val config = TradingPairsConfig(global.get[Configuration]("tradingPairs"))
    logger.info(s"Loading TradingPairsConfig, enabled = ${config.enabled}")

    config
  }

  @Provides
  def retryConfig(global: Configuration): RetryConfig = {
    val config = RetryConfig(global.get[Configuration]("retry"))
    logger.info(s"Loading RetryConfig, initialDelay = ${config.initialDelay}, maxDelay = ${config.maxDelay}")

    config
  }

  @Provides
  def discordConfig(global: Configuration): DiscordConfig = {
    val config = DiscordConfig(global.get[Configuration]("discord"))
    logger.info(
      s"Loading discordConfig, serverName = ${config.serverName}, channelName = ${config.channelName}, enabled = ${config.enabled}"
    )

    config
  }

  @Provides
  def explorerConfig(global: Configuration): ExplorerConfig = {
    val config = ExplorerConfig(global.get[Configuration]("explorer"))
    logger.info(
      s"Loading explorerConfig, urlApi = ${config.urlApi}}"
    )

    config
  }

  @Provides
  def ipInfoConfig(global: Configuration): IpInfoConfig = {

    val config = IpInfoConfig(global.get[Configuration]("ipinfo"))
    logger.info(
      s"Loading IpInfoConfig, urlApi = ${config.urlApi}, token =  ${config.token.take(2)}..${config.token.takeRight(2)}"
    )

    config
  }

  @Provides
  def channelRentalConfig(global: Configuration): ChannelRentalConfig = {
    val config = ChannelRentalConfig(global.get[Configuration]("channelRental"))
    logger.info(s"Loading ChannelRentalConfig, $config")

    config
  }

  @Provides
  def ethConfig(global: Configuration): ETHConfig = {
    val config = ETHConfig(global.get[Configuration]("eth"))
    logger.info(s"Loading ETHConfig, $config")

    config
  }
}
