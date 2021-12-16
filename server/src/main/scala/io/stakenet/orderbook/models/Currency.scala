package io.stakenet.orderbook.models

import enumeratum._
import scala.concurrent.duration._

sealed trait Currency extends EnumEntry with Product with Serializable {
  def rentChannelFeePercentage: BigDecimal
  def networkFee: Satoshis
  def forceChannelCloseAverageTime: FiniteDuration
  def digits: Int
  def satoshis(amount: BigInt): Option[Satoshis] = Satoshis.from(amount, digits)
  def satoshis(amount: String): Option[Satoshis] = Satoshis.from(amount, digits)
}

object Currency extends Enum[Currency] {
  val forLnd = List(XSN, BTC, LTC)
  val values = findValues

  final case object XSN extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("0.000004")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    override def forceChannelCloseAverageTime: FiniteDuration = 144.minutes
    override def digits: Int = 8
  }

  final case object BTC extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("0.00022712")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    override def forceChannelCloseAverageTime: FiniteDuration = 24.hours
    override def digits: Int = 8
  }

  final case object LTC extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("0.000004")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    override def forceChannelCloseAverageTime: FiniteDuration = 144.minutes
    override def digits: Int = 8
  }

  final case object WETH extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("0.025")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    // on connext we can withdraw funds from a channel at anytime without closing it
    override def forceChannelCloseAverageTime: FiniteDuration = 0.seconds
    override def digits: Int = 18
  }

  final case object USDT extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("100.0")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    // on connext we can withdraw funds from a channel at anytime without closing it
    override def forceChannelCloseAverageTime: FiniteDuration = 0.seconds
    override def digits: Int = 6
  }

  final case object ETH extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("0.025")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    // on connext we can withdraw funds from a channel at anytime without closing it
    override def forceChannelCloseAverageTime: FiniteDuration = 0.seconds
    override def digits: Int = 18
  }

  final case object USDC extends Currency {
    override def rentChannelFeePercentage: BigDecimal = BigDecimal(0.00004)

    override def networkFee: Satoshis =
      Satoshis.from(BigDecimal("100.0")).getOrElse(throw new RuntimeException("Invalid satoshis"))

    // on connext we can withdraw funds from a channel at anytime without closing it
    override def forceChannelCloseAverageTime: FiniteDuration = 0.seconds
    override def digits: Int = 6
  }
}
