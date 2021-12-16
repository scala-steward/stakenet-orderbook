package io.stakenet.orderbook.discord.commands.report

import java.time.Instant

import ackcord.data.GuildChannel
import com.google.inject.Inject
import io.stakenet.orderbook.discord.{CurrencyReport, DiscordAPI}
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.services.apis.PriceApi
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ReportCommandHandler @Inject() (
    reportsRepository: ReportsRepository.FutureImpl,
    priceApi: PriceApi,
    discordAPI: DiscordAPI
)(implicit
    ec: ExecutionContext
) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def process(channel: GuildChannel, command: ReportCommand): Unit = {
    val now = Instant.now()
    val from = now.minusSeconds(command.period.toSeconds)
    val futures = Currency.forLnd.map { currency =>
      for {
        channelReport <- reportsRepository.getChannelRentReport(from = from, to = now, currency = currency)
        tradesReport <- reportsRepository.getTradesFeeReport(currency = currency, from = from, to = now)
        priceMaybe <- priceApi.getUSDPrice(currency)
      } yield CurrencyReport(currency, priceMaybe, channelReport, tradesReport)
    }

    Future
      .sequence(futures)
      .map { reports =>
        renderReport(command, reports)
      }
      .onComplete {
        case Success(text) => discordAPI.sendMessage(channel, text)

        case Failure(exception) =>
          logger.error(s"Failed to generated report for the last ${command.period}", exception)
          discordAPI.sendMessage(channel, s"Failed to generate report: ${exception.getMessage}")
      }
  }

  private def renderUSD(value: BigDecimal): String = "%.2f USD".format(value)

  private def renderReport(command: ReportCommand, reports: List[CurrencyReport]): String = {
    val header = if (command.summary) {
      s"**Summary for the last ${command.period}**\n\n"
    } else {
      s"**Channel rentals and order fees for the last ${command.period}**\n\n"
    }

    def renderRentalValues: String = {
      val numRentals = reports.map(_.numberOfRentals).sum
      val numExtensions = reports.map(_.numberOfExtensions).sum

      // the income we get by the rental fee (0.0004%)
      val rentalIncomeRaw = reports.flatMap(_.rentingFeesUSD).sum
      val rentalIncomeUSD = renderUSD(rentalIncomeRaw)

      // the income we get by billing the client in advance to cover the onchain tx fees (open/close channel)
      val txFeeIncome = renderUSD(
        reports.flatMap(_.transactionsFeesUSD).sum + reports.flatMap(_.forceClosingFeesUSD).sum
      )

      // the income we get from people extending rental periods
      val rentalsExtensionsIncomeRaw = reports.flatMap(_.rentalsExtensionsRevenueUSD).sum
      val rentalsExtensionsIncome = renderUSD(reports.flatMap(_.rentalsExtensionsRevenueUSD).sum)

      // the money we spend on onchain tx while opening/closing channels
      val txFeeExpenses = renderUSD(
        -(reports.flatMap(_.fundingTxFeesUSD).sum + reports.flatMap(_.closingTxFeesUSD).sum)
      )

      val rentalProfitRaw = reports.flatMap(_.rentalsProfitUSD).sum
      val rentalsProfit = renderUSD(rentalProfitRaw)

      // (rental income + extension income) / (# of rentals + # of extensions)
      val averageRentalFee = renderUSD(
        Option(numRentals + numExtensions)
          .filter(_ > 0)
          .map(x => (rentalIncomeRaw + rentalsExtensionsIncomeRaw) / x)
          .getOrElse(0)
      )

      val averageRentalTime = Option(numRentals)
        .filter(_ > 0)
        .map(reports.map(_.rentalTimeHours).sum / _)
        .getOrElse(0)

      val averageRentalCapacityUSD = {
        val value = Option(numRentals)
          .filter(_ > 0)
          .map(total => reports.flatMap(_.totalCapacityUSD).sum / total)
          .getOrElse(BigDecimal(0))
        renderUSD(value)
      }

      s"""
         |- **Rentals count: $numRentals**
         |- **Rental fee income: $rentalIncomeUSD**
         |- **Tx fee income: $txFeeIncome**
         |- **Tx fee expenses: $txFeeExpenses**
         |- **Extensions count: $numExtensions**
         |- **Extensions revenue: $rentalsExtensionsIncome**
         |- **Average rental fee: $averageRentalFee**
         |- **Average rental time: $averageRentalTime hours**
         |- **Average rental capacity: $averageRentalCapacityUSD**
         |- **Profit: $rentalsProfit**""".stripMargin.trim
    }

    def renderOrderValues: String = {
      val totalVolume = renderUSD(reports.flatMap(_.volumeUSD).sum)
      val numOrders = reports.map(_.numberOfOrders).sum
      val ordersRevenue = renderUSD(reports.flatMap(_.orderFeesUSD).sum)
      val ordersRefunds = renderUSD(-reports.flatMap(_.orderRefundsUSD).sum)
      val ordersProfit = renderUSD(reports.flatMap(_.ordersProfitUSD).sum)

      s"""
         |- **Count: $numOrders**
         |- **Volume: $totalVolume**"
         |- **Fees: $ordersRevenue**
         |- **Refunds: $ordersRefunds**
         |- **Profit: $ordersProfit**"
         |""".stripMargin.trim
    }

    val footer = if (reports.forall(_.usdPriceMaybe.isDefined)) {
      val totalProfit = renderUSD(reports.flatMap(_.totalProfitUSD).sum)
      s"""
         |**Total Rentals**:
         |$renderRentalValues
         |
         |**Total Orders**:
         |$renderOrderValues
         |
         |**Total profit: $totalProfit**
         |""".stripMargin
    } else {
      "\nAggregated profit not available"
    }

    val body = if (command.summary) reports.map(_.renderSummary) else reports.map(_.render)
    body.mkString(header, "\n", footer)
  }
}
