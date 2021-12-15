package io.stakenet.orderbook.config

import io.stakenet.orderbook.models.Satoshis
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

case class ChannelRentalConfig(
    maxDuration: FiniteDuration,
    minDuration: FiniteDuration,
    maxCapacityUsd: BigDecimal,
    minCapacityUsd: BigDecimal,
    maxOnChainFeesUsd: BigDecimal,
    connextHubAddress: String,
    connextChannelContractFee: Satoshis
)

object ChannelRentalConfig {

  def apply(config: Configuration): ChannelRentalConfig = {
    val maxDuration = config.get[FiniteDuration]("maxDuration")
    val minDuration = config.get[FiniteDuration]("minDuration")
    val maxCapacityUsd = BigDecimal(config.get[String]("maxCapacityUsd"))
    val minCapacityUsd = BigDecimal(config.get[String]("minCapacityUsd"))
    val maxOnChainFeesUsd = BigDecimal(config.get[String]("maxOnChainFeesUsd"))
    val connextHubAddress = config.get[String]("connextHubAddress")
    val connextChannelContractFee = Satoshis
      .from(BigDecimal(config.get[String]("connextChannelContractFee")))
      .getOrElse(throw new RuntimeException("Invalid connextChannelContractFee"))

    ChannelRentalConfig(
      maxDuration = maxDuration,
      minDuration = minDuration,
      maxCapacityUsd = maxCapacityUsd,
      minCapacityUsd = minCapacityUsd,
      maxOnChainFeesUsd = maxOnChainFeesUsd,
      connextHubAddress = connextHubAddress,
      connextChannelContractFee = connextChannelContractFee
    )
  }
}
