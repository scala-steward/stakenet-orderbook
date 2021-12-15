package io.stakenet.orderbook.discord

import io.stakenet.orderbook.models.reports.{ChannelRentalReport, TradesFeeReport}
import io.stakenet.orderbook.models.{Currency, Satoshis}

import scala.concurrent.duration.DurationLong

case class CurrencyReport(
    currency: Currency,
    usdPriceMaybe: Option[BigDecimal],
    channelReport: ChannelRentalReport,
    tradesReport: TradesFeeReport
) {

  private val renderAmount = (satoshis: Satoshis) => {
    usdPriceMaybe
      .map(satoshis.toReadableUSDValue(currency, _))
      .getOrElse(satoshis.toString(currency))
  }

  private val renderBigDecimalAmount = (value: BigDecimal) => {
    val toReadable = "%.8f %s".format(value, currency.toString)

    usdPriceMaybe
      .map(price => "%s (%.2f USD)".format(toReadable, value * price))
      .getOrElse(toReadable)
  }

  private val usdRateHeader = usdPriceMaybe
    .map(p => s"(1 $currency = %.2f USD)".format(p))
    .getOrElse("(USD price not available)")

  private val totalProfit = tradesReport.profit + channelReport.profit

  val totalProfitUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * totalProfit
  }

  val ordersProfitUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * tradesReport.profit
  }

  val rentalsProfitUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.profit
  }

  val volumeUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * tradesReport.volume.toBigDecimal
  }

  val fundingTxFeesUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.fundingTxFee.toBigDecimal
  }

  val closingTxFeesUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.closingTxFee.toBigDecimal
  }

  val rentingFeesUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.rentingFees.toBigDecimal
  }

  val transactionsFeesUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.transactionFees.toBigDecimal
  }

  val forceClosingFeesUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.forceClosingFees.toBigDecimal
  }

  val numberOfRentals: Int = channelReport.numRentals
  val numberOfExtensions: Int = channelReport.numExtensions
  val numberOfOrders: BigInt = tradesReport.totalOrders

  val orderFeesUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * tradesReport.fee.toBigDecimal
  }

  val orderRefundsUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * tradesReport.refundedFee.toBigDecimal
  }

  val rentalsExtensionsRevenueUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * channelReport.extensionsRevenue.toBigDecimal
  }

  val rentalTimeHours: Long = channelReport.lifeTimeSeconds.seconds.toHours

  private val averageRentalHoursPerChannel: Long = {
    Option(numberOfRentals)
      .filter(_ > 0)
      .map(rentalTimeHours / _)
      .getOrElse(0)
  }

  val totalCapacity: Satoshis = channelReport.totalCapacity

  val totalCapacityUSD: Option[BigDecimal] = usdPriceMaybe.map { rate =>
    rate * totalCapacity.toBigDecimal
  }

  val averageRentalFee: String = {
    val avg = Option(numberOfRentals + numberOfExtensions)
      .filter(_ > 0)
      .map(total => (channelReport.rentingFees + channelReport.extensionsRevenue).toBigDecimal / total)
      .getOrElse(BigDecimal(0))
    renderBigDecimalAmount(avg)
  }

  val averageRentalCapacity: BigDecimal = {
    Option(numberOfRentals)
      .filter(_ > 0)
      .map(totalCapacity.toBigDecimal / _)
      .getOrElse(BigDecimal(0))
  }

  def renderSummary: String = {
    s"""
       |----------------------------------------------------------
       |**$currency profits $usdRateHeader**:
       |- Rentals: ${channelReport.numRentals}
       |- Average rental fee: $averageRentalFee
       |- Average rental time: $averageRentalHoursPerChannel hours
       |- Average rental capacity: ${renderBigDecimalAmount(averageRentalCapacity)}
       |- Orders: ${renderBigDecimalAmount(tradesReport.profit)}
       |- Total: **${renderBigDecimalAmount(totalProfit)}**""".stripMargin.trim
  }

  def render: String = {
    s"""
       |----------------------------------------------------------
       |**$currency $usdRateHeader**:
       |Channel Rentals
       |- Average rental fee: $averageRentalFee
       |- Average rental time: $averageRentalHoursPerChannel hours
       |- Average rental capacity: ${renderBigDecimalAmount(averageRentalCapacity)}
       |- Rental fee income: ${renderAmount(channelReport.rentingFees)}
       |- Tx fee income: ${renderAmount(channelReport.transactionFees + channelReport.forceClosingFees)}
       |- Tx fee expenses: -${renderAmount(channelReport.fundingTxFee + channelReport.closingTxFee)}
       |- Extensions revenue: ${renderAmount(channelReport.extensionsRevenue)}
       |- Number of rentals: ${channelReport.numRentals}
       |- Number of extensions: ${channelReport.numExtensions}
       |- **Profit**: ${renderBigDecimalAmount(channelReport.profit)}
       |
       |Order fees:
       |- Fees: ${renderAmount(tradesReport.fee)}
       |- Orders : ${tradesReport.totalOrders}
       |- Refunds: -${renderAmount(tradesReport.refundedFee)}
       |- **Volume**: ${renderAmount(tradesReport.volume)}
       |- **Profit**: ${renderBigDecimalAmount(tradesReport.profit)}
       |
       |**Total profit**: ${renderBigDecimalAmount(totalProfit)}
       |""".stripMargin.trim
  }
}
