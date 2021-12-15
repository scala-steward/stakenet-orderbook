package io.stakenet.orderbook.repositories.reports

import java.time.Instant

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.models.Currency.XSN
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd.{FeeRefund, LndTxid, PaymentRHash}
import io.stakenet.orderbook.models.reports._
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis, reports}
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import org.postgresql.util.PSQLException
import org.scalatest.OptionValues._

import scala.concurrent.duration._
import scala.util.Random

class ReportsPostgresRepositorySpec extends PostgresRepositorySpec {

  private lazy val repository = new ReportsPostgresRepository(database)
  val firstOfJuly2020 = Instant.ofEpochSecond(1593646902)
  val secondOfJuly2020 = Instant.ofEpochSecond(1593733302)
  val thirdOfJuly2020 = Instant.ofEpochSecond(1593819702)
  val fourthOfJuly2020 = Instant.ofEpochSecond(1593906102)
  val fifthOfJuly2020 = Instant.ofEpochSecond(1593992502)

  "getChannelRentReport" should {
    "return the report" in {

      createChannelFee(
        capacity = "0.000001",
        rentFee = "0.0001",
        openingFee = "0.000025",
        closingFee = "0.000005",
        date = firstOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00005",
        transactionFee = "0.000025",
        forceClosingFee = "0.000025"
      )
      createChannelRentalExtensionFee(Currency.XSN, Currency.BTC, "0.00001", firstOfJuly2020)

      createChannelFee(
        capacity = "0.000002",
        rentFee = "0.00025",
        openingFee = "0.00001",
        closingFee = "0.000015",
        date = secondOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00015",
        transactionFee = "0.00005",
        forceClosingFee = "0.00005"
      )
      createChannelRentalExtensionFee(Currency.XSN, Currency.BTC, "0.000025", secondOfJuly2020)

      createChannelFee(
        capacity = "0.000003",
        rentFee = "0.00015",
        openingFee = "0.000009",
        closingFee = "0.000003",
        date = thirdOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00008",
        transactionFee = "0.00004",
        forceClosingFee = "0.00003"
      )
      createChannelRentalExtensionFee(Currency.XSN, Currency.BTC, "0.000015", thirdOfJuly2020)

      createChannelFee(
        capacity = "0.000004",
        rentFee = "0.00018",
        openingFee = "0.0000035",
        closingFee = "0.0000075",
        date = fourthOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00012",
        transactionFee = "0.00005",
        forceClosingFee = "0.00001"
      )
      createChannelRentalExtensionFee(Currency.XSN, Currency.BTC, "0.000018", fourthOfJuly2020)

      createChannelFee(
        capacity = "0.000005",
        rentFee = "0.00023",
        openingFee = "0.00005",
        closingFee = "0.00003",
        date = fifthOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.0002",
        transactionFee = "0.00001",
        forceClosingFee = "0.00002"
      )
      createChannelRentalExtensionFee(Currency.XSN, Currency.BTC, "0.000023", fifthOfJuly2020)

      val expectedXSN = ChannelRentalReport(
        Currency.XSN,
        Helpers.asSatoshis("0.00035"),
        Helpers.asSatoshis("0.00014"),
        Helpers.asSatoshis("0.00009"),
        Helpers.asSatoshis("0.000058"),
        Satoshis.Zero,
        Satoshis.Zero,
        0,
        0,
        0,
        Satoshis.Zero
      )

      val expectedBTC = ChannelRentalReport(
        Currency.BTC,
        Satoshis.Zero,
        Satoshis.Zero,
        Satoshis.Zero,
        Satoshis.Zero,
        Helpers.asSatoshis("0.0000225"),
        Helpers.asSatoshis("0.0000255"),
        3,
        3,
        10800,
        Helpers.asSatoshis("0.000009")
      )

      val resultXSN = repository.getChannelRentReport(Currency.XSN, secondOfJuly2020, fourthOfJuly2020)
      val resultBTC = repository.getChannelRentReport(Currency.BTC, secondOfJuly2020, fourthOfJuly2020)

      resultXSN mustBe expectedXSN
      resultBTC mustBe expectedBTC
      resultXSN.profit mustBe BigDecimal(0.00063800)
      resultBTC.profit mustBe BigDecimal(-0.00004800)
    }

    "return the report including revenue and transaction fees" in {

      createChannelFee(
        capacity = "0.000001",
        rentFee = "0.0001",
        openingFee = "0.000025",
        closingFee = "0.000005",
        date = firstOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00005",
        transactionFee = "0.000025",
        forceClosingFee = "0.000025"
      )
      createChannelFee(
        capacity = "0.000002",
        rentFee = "0.00025",
        openingFee = "0.0001",
        closingFee = "0.00015",
        date = secondOfJuly2020,
        payingCurrency = Currency.BTC,
        rentedCurrency = Currency.XSN,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00015",
        transactionFee = "0.00005",
        forceClosingFee = "0.00005"
      )
      createChannelFee(
        capacity = "0.000003",
        rentFee = "0.00015",
        openingFee = "0.00009",
        closingFee = "0.00014",
        date = thirdOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00008",
        transactionFee = "0.00004",
        forceClosingFee = "0.00003"
      )
      createChannelFee(
        capacity = "0.000004",
        rentFee = "0.00018",
        openingFee = "0.000045",
        closingFee = "0.000175",
        date = fourthOfJuly2020,
        payingCurrency = Currency.BTC,
        rentedCurrency = Currency.XSN,
        lifeTimeSeconds = 3600,
        rentingFee = "0.00012",
        transactionFee = "0.00005",
        forceClosingFee = "0.00001"
      )
      createChannelFee(
        capacity = "0.000005",
        rentFee = "0.00023",
        openingFee = "0.00015",
        closingFee = "0.00013",
        date = fifthOfJuly2020,
        lifeTimeSeconds = 3600,
        rentingFee = "0.0002",
        transactionFee = "0.00001",
        forceClosingFee = "0.00002"
      )

      val expected = ChannelRentalReport(
        Currency.BTC,
        Helpers.asSatoshis("0.00027"),
        Helpers.asSatoshis("0.0001"),
        Helpers.asSatoshis("0.00006"),
        Satoshis.Zero,
        Helpers.asSatoshis("0.000265"),
        Helpers.asSatoshis("0.000275"),
        3,
        0,
        10800,
        Helpers.asSatoshis("0.000009")
      )
      val result = repository.getChannelRentReport(Currency.BTC, firstOfJuly2020, fifthOfJuly2020)

      result mustBe expected
      result.profit mustBe BigDecimal(-0.00011)

      val expected2 = ChannelRentalReport(
        Currency.XSN,
        Helpers.asSatoshis("0.00033"),
        Helpers.asSatoshis("0.000075"),
        Helpers.asSatoshis("0.000075"),
        Satoshis.Zero,
        Helpers.asSatoshis("0.000145"),
        Helpers.asSatoshis("0.000325"),
        2,
        0,
        7200,
        Helpers.asSatoshis("0.000006")
      )

      val result2 = repository.getChannelRentReport(Currency.XSN, firstOfJuly2020, fifthOfJuly2020)
      result2 mustBe expected2
      result2.profit mustBe BigDecimal(0.00001)
    }

    "return an empty report" in {
      val result = repository.getChannelRentReport(Currency.LTC, secondOfJuly2020, fourthOfJuly2020)
      val expected = ChannelRentalReport.empty(Currency.LTC)
      result mustBe expected
    }
  }

  "getTradesFeeReport" should {
    "return the trades report" in {
      createPartialOrder(XSN, "0.0006", secondOfJuly2020)
      createPartialOrder(XSN, "0.0006", thirdOfJuly2020)
      createPartialOrder(XSN, "0.0006", fourthOfJuly2020)

      createOrderFee(XSN, "0.00050", "0.000003", 0.0024, firstOfJuly2020)
      createOrderFee(XSN, "0.00075", "0.000002", 0.0024, secondOfJuly2020)
      createOrderFee(XSN, "0.00060", "0.000001", 0.0024, thirdOfJuly2020)
      createOrderFee(XSN, "0.00096", "0.0000005", 0.0024, fourthOfJuly2020)
      createOrderFee(XSN, "0.00032", "0.0000002", 0.0024, fifthOfJuly2020)

      val expected = TradesFeeReport(
        XSN,
        Helpers.asSatoshis("0.0000035"),
        Helpers.asSatoshis("0.0018"),
        3,
        Satoshis.Zero
      )
      val result = repository.getTradesFeeReport(Currency.XSN, secondOfJuly2020, fourthOfJuly2020)

      result mustBe expected
      result.profit mustBe Helpers.asSatoshis("0.0000035").toBigDecimal
    }

    "return the report with refunds" in {

      createPartialOrder(XSN, "0.0005", secondOfJuly2020)
      createPartialOrder(XSN, "0.0005", thirdOfJuly2020)
      createPartialOrder(XSN, "0.0005", fourthOfJuly2020)

      createOrderFee(XSN, "0.0005", "0.000003", 0.0024, firstOfJuly2020)
      createOrderFee(XSN, "0.00075", "0.000002", 0.0024, secondOfJuly2020)
      createOrderFee(XSN, "0.0006", "0.000001", 0.0024, thirdOfJuly2020)
      createOrderFee(XSN, "0.00096", "0.0000005", 0.0024, fourthOfJuly2020)
      createOrderFee(XSN, "0.00032", "0.0000002", 0.0024, fifthOfJuly2020)

      createFeeRefundReport(XSN, "0.000001", secondOfJuly2020)
      createFeeRefundReport(XSN, "0.000001", secondOfJuly2020)

      val expected = TradesFeeReport(
        XSN,
        Helpers.asSatoshis("0.0000035"),
        Helpers.asSatoshis("0.0015"),
        3,
        Helpers.asSatoshis("0.000002")
      )
      val result = repository.getTradesFeeReport(Currency.XSN, secondOfJuly2020, fourthOfJuly2020)

      result mustBe expected
      result.profit mustBe Helpers.asSatoshis("0.0000015").toBigDecimal
    }

    "return the report with the correct volume" in {
      val paymentHash1 = randomPaymentHash()
      val paymentHash2 = randomPaymentHash()

      createOrderFee(XSN, "0.0005", "0.000003", 0.0024, firstOfJuly2020, paymentHash1)
      createOrderFee(XSN, "0.00075", "0.000002", 0.0024, secondOfJuly2020, paymentHash2)
      createOrderFee(XSN, "0.0006", "0.000001", 0.0024, thirdOfJuly2020)
      createOrderFee(XSN, "0.00096", "0.0000005", 0.0024, fourthOfJuly2020)
      createOrderFee(XSN, "0.00032", "0.0000002", 0.0024, fifthOfJuly2020)

      createFeeRefundReport(XSN, "0.000001", secondOfJuly2020)
      createFeeRefundReport(XSN, "0.000001", secondOfJuly2020)

      createPartialOrder(XSN, "0.0005", firstOfJuly2020, paymentHash = Some(paymentHash1))
      createPartialOrder(XSN, "0.00075", secondOfJuly2020, paymentHash = Some(paymentHash1))
      createPartialOrder(XSN, "0.0006", thirdOfJuly2020)
      createPartialOrder(XSN, "0.00096", fourthOfJuly2020)
      createPartialOrder(XSN, "0.00032", fifthOfJuly2020)

      val expected = TradesFeeReport(
        XSN,
        Helpers.asSatoshis("0.0000035"),
        Helpers.asSatoshis("0.00231"),
        3,
        Helpers.asSatoshis("0.000002")
      )
      val result = repository.getTradesFeeReport(Currency.XSN, secondOfJuly2020, fourthOfJuly2020)

      result mustBe expected
      result.profit mustBe BigDecimal("0.0000015")
    }

    "return the report when there aren't any fees" in {
      createPartialOrder(XSN, "0.0005", firstOfJuly2020)
      createPartialOrder(XSN, "0.00075", secondOfJuly2020)
      createPartialOrder(XSN, "0.0006", thirdOfJuly2020)
      createPartialOrder(XSN, "0.00096", fourthOfJuly2020)
      createPartialOrder(XSN, "0.00032", fifthOfJuly2020)

      val expected = TradesFeeReport(XSN, Satoshis.Zero, Helpers.asSatoshis("0.00231000"), 3, Satoshis.Zero)
      val result = repository.getTradesFeeReport(Currency.XSN, secondOfJuly2020, fourthOfJuly2020)

      result mustBe expected
      result.profit mustBe 0
    }
  }

  "createChannelRentalExtensionFee" should {
    "create a channel rental extension fee" in {
      val fee = ChannelRentalExtensionFee(randomPaymentHash(), Currency.XSN, Currency.BTC, Satoshis.One, Instant.now)

      repository.createChannelRentalExtensionFee(fee)

      succeed
    }

    "fail on duplicated channel rental extension fee" in {
      val fee = ChannelRentalExtensionFee(randomPaymentHash(), Currency.XSN, Currency.BTC, Satoshis.One, Instant.now)

      repository.createChannelRentalExtensionFee(fee)
      val error = intercept[PSQLException] {
        repository.createChannelRentalExtensionFee(fee)
      }

      error.getMessage mustBe s"Channel rental extension fee ${fee.paymentHash} already exists"
    }
  }

  "createChannelRentalFeeDetail" should {
    "create a channel rental fee " in {
      val detail = ChannelRentalFeeDetail(
        randomPaymentHash(),
        Currency.XSN,
        Helpers.asSatoshis("0.00025"),
        Helpers.asSatoshis("0.00005"),
        Helpers.asSatoshis("0.0001")
      )

      repository.createChannelRentalFeeDetail(detail)

      succeed
    }

    "fail on duplicated channel rental fee detail" in {
      val detail = ChannelRentalFeeDetail(
        randomPaymentHash(),
        Currency.XSN,
        Helpers.asSatoshis("0.00025"),
        Helpers.asSatoshis("0.00005"),
        Helpers.asSatoshis("0.0001")
      )

      repository.createChannelRentalFeeDetail(detail)
      val error = intercept[PSQLException] {
        repository.createChannelRentalFeeDetail(detail)
      }

      error.getMessage mustBe s"Channel rental fee detail ${detail.paymentHash} for ${detail.currency} already exists"
    }
  }

  "getClientsLastGreenStatus" should {
    "return correct values" in {
      val clientId1 = createClient()
      val clientId2 = createClient()

      logClientInfo(clientId1, 100, 0, thirdOfJuly2020)
      logClientInfo(clientId1, 50, 100, secondOfJuly2020)
      logClientInfo(clientId2, 100, 100, thirdOfJuly2020)
      logClientInfo(clientId2, 150, 50, firstOfJuly2020)

      val result = repository.getClientsStatusReport()

      implicit val ordering: Ordering[ClientStatus] = Ordering.by(c => c.clientId.toString)
      val expected = List(
        reports.ClientStatus(clientId1, "Wallet", 100, 0, 1.day),
        reports.ClientStatus(clientId2, "Wallet", 100, 100, 2.day)
      ).sorted

      result mustBe expected
    }

    "ignore clients without logs" in {
      val clientId = createClient()
      createClient()
      createClient()

      logClientInfo(clientId, 100, 0, thirdOfJuly2020)
      logClientInfo(clientId, 100, 100, secondOfJuly2020)
      logClientInfo(clientId, 50, 100, firstOfJuly2020)

      val result = repository.getClientsStatusReport()

      val expected = List(
        reports.ClientStatus(clientId, "Wallet", 100, 0, 2.day)
      )

      result mustBe expected
    }

    "return empty lists when there are no logs" in {
      createClient()
      createClient()

      val result = repository.getClientsStatusReport()

      result mustBe List.empty
    }
  }

  private def createChannelFee(
      capacity: String,
      rentFee: String,
      openingFee: String,
      closingFee: String,
      date: Instant,
      payingCurrency: Currency = Currency.XSN,
      rentedCurrency: Currency = Currency.BTC,
      lifeTimeSeconds: Long,
      rentingFee: String,
      transactionFee: String,
      forceClosingFee: String
  ): Unit = {
    val paymentHash = randomPaymentHash()

    val detail = ChannelRentalFeeDetail(
      paymentHash,
      payingCurrency,
      Helpers.asSatoshis(rentingFee),
      Helpers.asSatoshis(transactionFee),
      Helpers.asSatoshis(forceClosingFee)
    )

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

    repository.createChannelRentalFeeDetail(detail)
    repository.createChannelRentalFee(channelRentalFee)
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
      feePercent: BigDecimal,
      date: Instant,
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

    repository.createOrderFeePayment(orderFeePayment)
  }

  private def createPartialOrder(
      currency: Currency,
      tradedAmount: String,
      createdAt: Instant,
      paymentHash: Option[PaymentRHash] = None
  ): Unit = {
    val clientsRepository = new ClientsPostgresRepository(database)
    val clientId = clientsRepository.createWalletClient(Helpers.randomWalletId())

    val partialOrder = PartialOrder(
      OrderId.random(),
      clientId,
      paymentHash,
      currency,
      Helpers.asSatoshis(tradedAmount),
      createdAt
    )

    repository.createPartialOrder(partialOrder)
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

    repository.createChannelRentalExtensionFee(extensionFee)
  }

  private def createFeeRefundReport(currency: Currency, amount: String, date: Instant): Unit = {
    val feeRefundsReport = FeeRefundsReport(FeeRefund.Id.random(), currency, Helpers.asSatoshis(amount), date)
    repository.createFeeRefundedReport(feeRefundsReport)
  }

  private def createClient(): ClientId = {
    val clientsRepository = new ClientsPostgresRepository(database)

    clientsRepository.createWalletClient(Helpers.randomWalletId())
  }

  private def logClientInfo(
      clientId: ClientId,
      rentedCapacityUSD: BigDecimal,
      hubLocalBalanceUSD: BigDecimal,
      date: Instant
  ): Unit = {
    val clientsRepository = new ClientsPostgresRepository(database)

    clientsRepository.logClientInfo(clientId, rentedCapacityUSD, hubLocalBalanceUSD, date)
  }
}
