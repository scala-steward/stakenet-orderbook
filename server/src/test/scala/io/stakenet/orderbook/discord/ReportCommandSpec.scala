package io.stakenet.orderbook.discord

import java.time.Instant

import ackcord.APIMessage
import ackcord.data.{GuildChannel, Message}
import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.discord.commands.ExternalCommandHandler
import io.stakenet.orderbook.discord.commands.clientsStatus.ClientsStatusCommandHandler
import io.stakenet.orderbook.discord.commands.maintenance.MaintenanceCommandHandler
import io.stakenet.orderbook.discord.commands.report.{ReportCommand, ReportCommandHandler}
import io.stakenet.orderbook.helpers.Executors.databaseEC
import io.stakenet.orderbook.models.lnd.{FeeRefund, LndTxid, PaymentRHash}
import io.stakenet.orderbook.models.reports._
import io.stakenet.orderbook.models.{Currency, OrderId}
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.reports.{ReportsPostgresRepository, ReportsRepository}
import io.stakenet.orderbook.services.apis.PriceApi
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.util.Random

class ReportCommandSpec extends PostgresRepositorySpec {

  private lazy val reportsRepository = new ReportsPostgresRepository(database)

  val usdRates = Map(
    Currency.BTC -> 10000,
    Currency.XSN -> 5000,
    Currency.LTC -> 2500
  )

  "!report" should {
    "generate a report of the current day" in {
      val discordAPI = mock[DiscordAPI]
      val priceApi = mock[PriceApi]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI, priceApi)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!report day")

      Currency.forLnd.foreach { currency =>
        val rate = usdRates.get(currency).value

        when(priceApi.getUSDPrice(currency)).thenReturn(Future.successful(Some(rate)))
      }

      createOrderFee(Currency.XSN, "0.001", "0.0001", Instant.now)
      createOrderFee(Currency.BTC, "0.002", "0.0002", Instant.now)
      createOrderFee(Currency.LTC, "0.003", "0.0003", Instant.now)

      createFeeRefundReport(Currency.XSN, "0.00005", Instant.now)
      createFeeRefundReport(Currency.BTC, "0.00006", Instant.now)
      createFeeRefundReport(Currency.LTC, "0.00007", Instant.now)

      createPartialOrder(Currency.XSN, "0.0008")
      createPartialOrder(Currency.BTC, "0.0015")
      createPartialOrder(Currency.LTC, "0.00225")

      val paymentHash1 = randomPaymentHash()
      createChannelFee(
        capacity = "0.01",
        rentFee = "0.000025",
        openingFee = "0.0000015",
        closingFee = "0.0000018",
        date = Instant.now,
        payingCurrency = Currency.XSN,
        rentedCurrency = Currency.BTC,
        lifeTimeSeconds = 3600,
        paymentHash1
      )
      createChannelFeeDetail(
        rentingFee = "0.000015",
        transactionsFee = "0.000003",
        forceClosingFee = "0.000007",
        Currency.XSN,
        paymentHash1
      )

      val paymentHash2 = randomPaymentHash()
      createChannelFee(
        capacity = "0.02",
        rentFee = "0.000035",
        openingFee = "0.00001",
        closingFee = "0.000008",
        date = Instant.now,
        payingCurrency = Currency.BTC,
        rentedCurrency = Currency.LTC,
        lifeTimeSeconds = 3600,
        paymentHash2
      )
      createChannelFeeDetail(
        rentingFee = "0.00002",
        transactionsFee = "0.000005",
        forceClosingFee = "0.00001",
        Currency.BTC,
        paymentHash2
      )

      val paymentHash3 = randomPaymentHash()
      createChannelFee(
        capacity = "0.03",
        rentFee = "0.00003",
        openingFee = "0.0000022",
        closingFee = "0.0000025",
        date = Instant.now,
        payingCurrency = Currency.LTC,
        rentedCurrency = Currency.XSN,
        lifeTimeSeconds = 7200,
        paymentHash3
      )
      createChannelFeeDetail(
        rentingFee = "0.000018",
        transactionsFee = "0.000001",
        forceClosingFee = "0.000011",
        Currency.LTC,
        paymentHash3
      )

      createChannelRentalExtensionFee(
        payingCurrency = Currency.XSN,
        rentedCurrency = Currency.BTC,
        amount = "0.00002",
        Instant.now
      )
      createChannelRentalExtensionFee(
        payingCurrency = Currency.BTC,
        rentedCurrency = Currency.LTC,
        amount = "0.000025",
        Instant.now
      )
      createChannelRentalExtensionFee(
        payingCurrency = Currency.LTC,
        rentedCurrency = Currency.XSN,
        amount = "0.000005",
        Instant.now
      )

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """**Channel rentals and order fees for the last 1 day**
          |
          |----------------------------------------------------------
          |**XSN (1 XSN = 5000.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00001750 XSN (0.09 USD)
          |- Average rental time: 2 hours
          |- Average rental capacity: 0.03000000 XSN (150.00 USD)
          |- Rental fee income: 0.00001500 XSN (0.08 USD)
          |- Tx fee income: 0.00001000 XSN (0.05 USD)
          |- Tx fee expenses: -0.00000470 XSN (0.02 USD)
          |- Extensions revenue: 0.00002000 XSN (0.10 USD)
          |- Number of rentals: 1
          |- Number of extensions: 1
          |- **Profit**: 0.00004030 XSN (0.20 USD)
          |
          |Order fees:
          |- Fees: 0.00010000 XSN (0.50 USD)
          |- Orders : 1
          |- Refunds: -0.00005000 XSN (0.25 USD)
          |- **Volume**: 0.00080000 XSN (4.00 USD)
          |- **Profit**: 0.00005000 XSN (0.25 USD)
          |
          |**Total profit**: 0.00009030 XSN (0.45 USD)
          |----------------------------------------------------------
          |**BTC (1 BTC = 10000.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00002250 BTC (0.23 USD)
          |- Average rental time: 1 hours
          |- Average rental capacity: 0.01000000 BTC (100.00 USD)
          |- Rental fee income: 0.00002000 BTC (0.20 USD)
          |- Tx fee income: 0.00001500 BTC (0.15 USD)
          |- Tx fee expenses: -0.00000330 BTC (0.03 USD)
          |- Extensions revenue: 0.00002500 BTC (0.25 USD)
          |- Number of rentals: 1
          |- Number of extensions: 1
          |- **Profit**: 0.00005670 BTC (0.57 USD)
          |
          |Order fees:
          |- Fees: 0.00020000 BTC (2.00 USD)
          |- Orders : 1
          |- Refunds: -0.00006000 BTC (0.60 USD)
          |- **Volume**: 0.00150000 BTC (15.00 USD)
          |- **Profit**: 0.00014000 BTC (1.40 USD)
          |
          |**Total profit**: 0.00019670 BTC (1.97 USD)
          |----------------------------------------------------------
          |**LTC (1 LTC = 2500.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00001150 LTC (0.03 USD)
          |- Average rental time: 1 hours
          |- Average rental capacity: 0.02000000 LTC (50.00 USD)
          |- Rental fee income: 0.00001800 LTC (0.05 USD)
          |- Tx fee income: 0.00001200 LTC (0.03 USD)
          |- Tx fee expenses: -0.00001800 LTC (0.05 USD)
          |- Extensions revenue: 0.00000500 LTC (0.01 USD)
          |- Number of rentals: 1
          |- Number of extensions: 1
          |- **Profit**: 0.00001700 LTC (0.04 USD)
          |
          |Order fees:
          |- Fees: 0.00030000 LTC (0.75 USD)
          |- Orders : 1
          |- Refunds: -0.00007000 LTC (0.18 USD)
          |- **Volume**: 0.00225000 LTC (5.63 USD)
          |- **Profit**: 0.00023000 LTC (0.58 USD)
          |
          |**Total profit**: 0.00024700 LTC (0.62 USD)
          |**Total Rentals**:
          |- **Rentals count: 3**
          |- **Rental fee income: 0.32 USD**
          |- **Tx fee income: 0.23 USD**
          |- **Tx fee expenses: -0.10 USD**
          |- **Extensions count: 3**
          |- **Extensions revenue: 0.36 USD**
          |- **Average rental fee: 0.11 USD**
          |- **Average rental time: 1 hours**
          |- **Average rental capacity: 100.00 USD**
          |- **Profit: 0.81 USD**
          |
          |**Total Orders**:
          |- **Count: 3**
          |- **Volume: 24.63 USD**"
          |- **Fees: 3.25 USD**
          |- **Refunds: -1.03 USD**
          |- **Profit: 2.23 USD**"
          |
          |**Total profit: 3.04 USD**""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }

    "render empty report when database is empty" in {
      val discordAPI = mock[DiscordAPI]
      val priceApi = mock[PriceApi]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI, priceApi)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!report day")

      Currency.forLnd.foreach { currency =>
        val rate = usdRates.get(currency).value

        when(priceApi.getUSDPrice(currency)).thenReturn(Future.successful(Some(rate)))
      }

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """**Channel rentals and order fees for the last 1 day**
          |
          |----------------------------------------------------------
          |**XSN (1 XSN = 5000.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00000000 XSN (0.00 USD)
          |- Average rental time: 0 hours
          |- Average rental capacity: 0.00000000 XSN (0.00 USD)
          |- Rental fee income: 0.00000000 XSN (0.00 USD)
          |- Tx fee income: 0.00000000 XSN (0.00 USD)
          |- Tx fee expenses: -0.00000000 XSN (0.00 USD)
          |- Extensions revenue: 0.00000000 XSN (0.00 USD)
          |- Number of rentals: 0
          |- Number of extensions: 0
          |- **Profit**: 0.00000000 XSN (0.00 USD)
          |
          |Order fees:
          |- Fees: 0.00000000 XSN (0.00 USD)
          |- Orders : 0
          |- Refunds: -0.00000000 XSN (0.00 USD)
          |- **Volume**: 0.00000000 XSN (0.00 USD)
          |- **Profit**: 0.00000000 XSN (0.00 USD)
          |
          |**Total profit**: 0.00000000 XSN (0.00 USD)
          |----------------------------------------------------------
          |**BTC (1 BTC = 10000.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00000000 BTC (0.00 USD)
          |- Average rental time: 0 hours
          |- Average rental capacity: 0.00000000 BTC (0.00 USD)
          |- Rental fee income: 0.00000000 BTC (0.00 USD)
          |- Tx fee income: 0.00000000 BTC (0.00 USD)
          |- Tx fee expenses: -0.00000000 BTC (0.00 USD)
          |- Extensions revenue: 0.00000000 BTC (0.00 USD)
          |- Number of rentals: 0
          |- Number of extensions: 0
          |- **Profit**: 0.00000000 BTC (0.00 USD)
          |
          |Order fees:
          |- Fees: 0.00000000 BTC (0.00 USD)
          |- Orders : 0
          |- Refunds: -0.00000000 BTC (0.00 USD)
          |- **Volume**: 0.00000000 BTC (0.00 USD)
          |- **Profit**: 0.00000000 BTC (0.00 USD)
          |
          |**Total profit**: 0.00000000 BTC (0.00 USD)
          |----------------------------------------------------------
          |**LTC (1 LTC = 2500.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00000000 LTC (0.00 USD)
          |- Average rental time: 0 hours
          |- Average rental capacity: 0.00000000 LTC (0.00 USD)
          |- Rental fee income: 0.00000000 LTC (0.00 USD)
          |- Tx fee income: 0.00000000 LTC (0.00 USD)
          |- Tx fee expenses: -0.00000000 LTC (0.00 USD)
          |- Extensions revenue: 0.00000000 LTC (0.00 USD)
          |- Number of rentals: 0
          |- Number of extensions: 0
          |- **Profit**: 0.00000000 LTC (0.00 USD)
          |
          |Order fees:
          |- Fees: 0.00000000 LTC (0.00 USD)
          |- Orders : 0
          |- Refunds: -0.00000000 LTC (0.00 USD)
          |- **Volume**: 0.00000000 LTC (0.00 USD)
          |- **Profit**: 0.00000000 LTC (0.00 USD)
          |
          |**Total profit**: 0.00000000 LTC (0.00 USD)
          |**Total Rentals**:
          |- **Rentals count: 0**
          |- **Rental fee income: 0.00 USD**
          |- **Tx fee income: 0.00 USD**
          |- **Tx fee expenses: 0.00 USD**
          |- **Extensions count: 0**
          |- **Extensions revenue: 0.00 USD**
          |- **Average rental fee: 0.00 USD**
          |- **Average rental time: 0 hours**
          |- **Average rental capacity: 0.00 USD**
          |- **Profit: 0.00 USD**
          |
          |**Total Orders**:
          |- **Count: 0**
          |- **Volume: 0.00 USD**"
          |- **Fees: 0.00 USD**
          |- **Refunds: 0.00 USD**
          |- **Profit: 0.00 USD**"
          |
          |**Total profit: 0.00 USD**""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }

    "render the report even when a currency usd rate is not available" in {
      val discordAPI = mock[DiscordAPI]
      val priceApi = mock[PriceApi]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI, priceApi)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!report day")
      when(priceApi.getUSDPrice(Currency.XSN)).thenReturn(Future.successful(None))

      Currency.forLnd.filter(_ != Currency.XSN).foreach { currency =>
        val rate = usdRates.get(currency).value

        when(priceApi.getUSDPrice(currency)).thenReturn(Future.successful(Some(rate)))
      }

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """**Channel rentals and order fees for the last 1 day**
          |
          |----------------------------------------------------------
          |**XSN (USD price not available)**:
          |Channel Rentals
          |- Average rental fee: 0.00000000 XSN
          |- Average rental time: 0 hours
          |- Average rental capacity: 0.00000000 XSN
          |- Rental fee income: 0.00000000 XSN
          |- Tx fee income: 0.00000000 XSN
          |- Tx fee expenses: -0.00000000 XSN
          |- Extensions revenue: 0.00000000 XSN
          |- Number of rentals: 0
          |- Number of extensions: 0
          |- **Profit**: 0.00000000 XSN
          |
          |Order fees:
          |- Fees: 0.00000000 XSN
          |- Orders : 0
          |- Refunds: -0.00000000 XSN
          |- **Volume**: 0.00000000 XSN
          |- **Profit**: 0.00000000 XSN
          |
          |**Total profit**: 0.00000000 XSN
          |----------------------------------------------------------
          |**BTC (1 BTC = 10000.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00000000 BTC (0.00 USD)
          |- Average rental time: 0 hours
          |- Average rental capacity: 0.00000000 BTC (0.00 USD)
          |- Rental fee income: 0.00000000 BTC (0.00 USD)
          |- Tx fee income: 0.00000000 BTC (0.00 USD)
          |- Tx fee expenses: -0.00000000 BTC (0.00 USD)
          |- Extensions revenue: 0.00000000 BTC (0.00 USD)
          |- Number of rentals: 0
          |- Number of extensions: 0
          |- **Profit**: 0.00000000 BTC (0.00 USD)
          |
          |Order fees:
          |- Fees: 0.00000000 BTC (0.00 USD)
          |- Orders : 0
          |- Refunds: -0.00000000 BTC (0.00 USD)
          |- **Volume**: 0.00000000 BTC (0.00 USD)
          |- **Profit**: 0.00000000 BTC (0.00 USD)
          |
          |**Total profit**: 0.00000000 BTC (0.00 USD)
          |----------------------------------------------------------
          |**LTC (1 LTC = 2500.00 USD)**:
          |Channel Rentals
          |- Average rental fee: 0.00000000 LTC (0.00 USD)
          |- Average rental time: 0 hours
          |- Average rental capacity: 0.00000000 LTC (0.00 USD)
          |- Rental fee income: 0.00000000 LTC (0.00 USD)
          |- Tx fee income: 0.00000000 LTC (0.00 USD)
          |- Tx fee expenses: -0.00000000 LTC (0.00 USD)
          |- Extensions revenue: 0.00000000 LTC (0.00 USD)
          |- Number of rentals: 0
          |- Number of extensions: 0
          |- **Profit**: 0.00000000 LTC (0.00 USD)
          |
          |Order fees:
          |- Fees: 0.00000000 LTC (0.00 USD)
          |- Orders : 0
          |- Refunds: -0.00000000 LTC (0.00 USD)
          |- **Volume**: 0.00000000 LTC (0.00 USD)
          |- **Profit**: 0.00000000 LTC (0.00 USD)
          |
          |**Total profit**: 0.00000000 LTC (0.00 USD)
          |Aggregated profit not available""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }

    "fail for an unknown command" in {
      val discordAPI = mock[DiscordAPI]
      val priceApi = mock[PriceApi]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI, priceApi)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!report year")

      commandHandler.handlePossibleCommand(channel, command)

      val expected = s"Got unknown command, ${ReportCommand.help}"
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, expected)
    }

    "fail when priceAPI throws an exception" in {
      val discordAPI = mock[DiscordAPI]
      val priceApi = mock[PriceApi]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI, priceApi)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!report day")

      Currency.forLnd.foreach { currency =>
        when(priceApi.getUSDPrice(currency)).thenReturn(Future.failed(new RuntimeException("error")))
      }

      commandHandler.handlePossibleCommand(channel, command)

      val expected = s"Failed to generate report: error"
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, expected)
    }
  }

  private def getCommandHandler(discordAPI: DiscordAPI, priceApi: PriceApi) = {
    val reportCommandHandler = new ReportCommandHandler(
      new ReportsRepository.FutureImpl(reportsRepository),
      priceApi,
      discordAPI
    )

    new ExternalCommandHandler(
      reportCommandHandler,
      mock[MaintenanceCommandHandler],
      mock[ClientsStatusCommandHandler],
      discordAPI
    )
  }

  private def createChannelFee(
      capacity: String,
      rentFee: String,
      openingFee: String,
      closingFee: String,
      date: Instant,
      payingCurrency: Currency,
      rentedCurrency: Currency,
      lifeTimeSeconds: Long,
      paymentHash: PaymentRHash
  ): Unit = {
    val channelRentalFee = ChannelRentalFee(
      paymentHash,
      payingCurrency,
      rentedCurrency,
      Helpers.asSatoshis(rentFee),
      Helpers.asSatoshis(capacity),
      randomTransactionHash(),
      Helpers.asSatoshis(openingFee),
      randomTransactionHash(),
      Helpers.asSatoshis(closingFee),
      date,
      lifeTimeSeconds
    )

    reportsRepository.createChannelRentalFee(channelRentalFee)
  }

  private def createChannelFeeDetail(
      rentingFee: String,
      transactionsFee: String,
      forceClosingFee: String,
      payingCurrency: Currency,
      paymentHash: PaymentRHash
  ): Unit = {
    val detail = ChannelRentalFeeDetail(
      paymentHash,
      payingCurrency,
      Helpers.asSatoshis(rentingFee),
      Helpers.asSatoshis(transactionsFee),
      Helpers.asSatoshis(forceClosingFee)
    )

    reportsRepository.createChannelRentalFeeDetail(detail)
  }

  private def randomTransactionHash(): LndTxid = {
    val data = Random.alphanumeric.take(32).mkString.getBytes
    val hexData = data.map("%02X".format(_)).mkString

    LndTxid.untrusted(hexData).value
  }

  private def createOrderFee(
      currency: Currency,
      fundsAmount: String,
      feeAmount: String,
      date: Instant,
      feePercent: BigDecimal = 0.25,
      paymentRHash: PaymentRHash = randomPaymentHash()
  ): Unit = {
    val orderFeePayment = OrderFeePayment(
      paymentRHash,
      currency,
      Helpers.asSatoshis(fundsAmount),
      Helpers.asSatoshis(feeAmount),
      feePercent,
      date
    )

    reportsRepository.createOrderFeePayment(orderFeePayment)
  }

  private def createPartialOrder(
      currency: Currency,
      amount: String,
      createdAt: Instant = Instant.now(),
      paymentHash: Option[PaymentRHash] = None
  ): Unit = {
    val clientsRepository = new ClientsPostgresRepository(database)
    val clientId = clientsRepository.createWalletClient(Helpers.randomWalletId())

    val partialOrder = PartialOrder(
      OrderId.random(),
      clientId,
      paymentHash,
      currency,
      Helpers.asSatoshis(amount),
      createdAt
    )

    reportsRepository.createPartialOrder(partialOrder)
  }

  private def createChannelRentalExtensionFee(
      payingCurrency: Currency,
      rentedCurrency: Currency,
      amount: String,
      createdAt: Instant
  ): Unit = {
    val extensionFee = ChannelRentalExtensionFee(
      randomPaymentHash(),
      payingCurrency,
      rentedCurrency,
      Helpers.asSatoshis(amount),
      createdAt
    )

    reportsRepository.createChannelRentalExtensionFee(extensionFee)
  }

  private def createFeeRefundReport(currency: Currency, amount: String, date: Instant): Unit = {
    val feeRefundsReport = FeeRefundsReport(FeeRefund.Id.random(), currency, Helpers.asSatoshis(amount), date)
    reportsRepository.createFeeRefundedReport(feeRefundsReport)
  }
}
