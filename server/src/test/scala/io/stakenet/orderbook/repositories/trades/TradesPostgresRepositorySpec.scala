package io.stakenet.orderbook.repositories.trades

import java.time.Instant
import java.util.UUID

import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.trading.TradingPair.{LTC_BTC, XSN_BTC, XSN_LTC}
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis, TradingPairPrice}
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.currencyPrices.CurrencyPricesPostgresRepository
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

import scala.concurrent.duration.DurationInt
import scala.util.Random

class TradesPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new TradesPostgresRepository(database, new TradesDAO)

  /**
   * A custom ordering for UUID is required one because the one from Java doesn't actually compares the bytes
   * but their signed representations which could cause weird tests failures on tests.
   *
   * Postgres actually sorts UUIDs by their byte representation.
   */
  implicit val tradeIdOrdering: Ordering[Trade.Id] = (x: Trade.Id, y: Trade.Id) => {
    val a = x.value.toString.replace("-", "")
    val b = y.value.toString.replace("-", "")
    BigInt(a, 16).compareTo(BigInt(b, 16))
  }

  "create" should {
    "create a new trade" in {
      val trade = newTrade(XSN_BTC)

      repository.create(trade)
      succeed
    }

    "fail to reuse the same trade id" in {
      val trade = newTrade(XSN_BTC)

      repository.create(trade)
      intercept[PSQLException] {
        repository.create(trade)
      }
    }
  }

  "find" should {
    "return an existing trade" in {
      val trade = newTrade(XSN_BTC)
      repository.create(trade)

      val result = repository.find(trade.id)
      result.value must be(trade)
    }

    "find nothing when the trade is not there" in {
      val trade = newTrade(XSN_BTC)
      repository.create(trade)

      val result = repository.find(Trade.Id(UUID.randomUUID()))
      result must be(empty)
    }
  }

  "getTrades" should {
    "get no trades" in {
      val result = repository.getTrades(10, None, XSN_BTC)
      result must be(empty)
    }

    "get no more trades than the limit" in {
      val limit = 2
      List.fill(limit + 1)(newTrade(XSN_LTC)).foreach(repository.create)
      val result = repository.getTrades(limit, None, XSN_LTC)
      result.size must be(limit)
    }

    "sort by time from newest to oldest, breaking ties by the id" in {
      val trade1 = newTrade(XSN_BTC)
      val instant = trade1.executedOn.plusSeconds(10)
      val List(id2, id3) = List.fill(2)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant, id = id3)
      List(trade3, trade1, trade2).foreach(repository.create)

      val result = repository.getTrades(10, None, XSN_BTC)

      result.size must be(3)
      result.zip(List(trade2, trade3, trade1)).foreach {
        case (actual, expected) =>
          actual must be(expected)
      }
    }

    "get no trades when the given trade for pagination doesn't exist" in {
      List.fill(3)(newTrade(XSN_BTC)).foreach(repository.create)
      val result = repository.getTrades(10, Some(Trade.Id(UUID.randomUUID())), XSN_BTC)
      result must be(empty)
    }

    "sort by time from newest to oldest, breaking ties by the id even if the last seen trade has a repeated time" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(10)
      val trade1 = newTrade(XSN_LTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_LTC).copy(executedOn = instant1, id = id2)
      val trade3 = newTrade(XSN_LTC).copy(executedOn = instant2, id = id3)
      val trade4 = newTrade(XSN_LTC).copy(executedOn = instant2, id = id4)
      List(trade3, trade1, trade4, trade2).foreach(repository.create)

      val result = repository.getTrades(10, Some(trade3.id), XSN_LTC)
      result.size must be(3)

      result.zip(List(trade4, trade1, trade2)).foreach {
        case (actual, expected) =>
          actual must be(expected)
      }
    }

    "get XSN_BTC trades" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(10)
      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_LTC).copy(executedOn = instant1, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id3)
      val trade4 = newTrade(XSN_LTC).copy(executedOn = instant2, id = id4)
      List(trade3, trade1, trade4, trade2).foreach(repository.create)

      val result = repository.getTrades(10, None, XSN_BTC)
      result.size must be(2)
      result.forall(_.pair == XSN_BTC) must be(true)

    }

    "get XSN_LTC trades" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(10)
      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_LTC).copy(executedOn = instant1, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id3)
      val trade4 = newTrade(XSN_LTC).copy(executedOn = instant2, id = id4)
      List(trade3, trade1, trade4, trade2).foreach(repository.create)

      val result = repository.getTrades(10, None, XSN_LTC)
      result.size must be(2)
      result.forall(_.pair == XSN_LTC) must be(true)
    }

    "get XSN_LTC trades paginated" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(10)
      val instant3 = instant2.plusSeconds(10)
      val instant4 = instant3.plusSeconds(10)
      val trade1 = newTrade(XSN_LTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_LTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_LTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_LTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val result = repository.getTrades(10, Some(trade3.id), XSN_LTC)
      val expected = List(trade2, trade1)
      result.size must be(2)
      result must be(expected)

    }

    "get XSN_BTC bars prices" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(10)
      val instant3 = instant2.plusSeconds(10)
      val instant4 = instant3.plusSeconds(10)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      val listTrades = List(trade1, trade2, trade3, trade4)

      listTrades.foreach(repository.create)
      val high = listTrades.maxBy(_.price)
      val low = listTrades.minBy(_.price)

      val from = instant1.minusSeconds(172800)
      val to = instant1.plusSeconds(172800)
      val result = repository.getBars(XSN_BTC, new Resolution(minutes = 1), from, to, 20)

      result.size must be(1)
      result.head.volume must be(4)
      result.head.high must be(high.price)
      result.head.low must be(low.price)
    }

    "get more then one XSN_BTC bars prices" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(3600)
      val instant3 = instant2.plusSeconds(3600)
      val instant4 = instant3.plusSeconds(3600)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      val listTrades = List(trade1, trade2, trade3, trade4)

      listTrades.foreach(repository.create)

      val from = instant1.minusSeconds(172800)
      val to = instant1.plusSeconds(172800)
      val result = repository.getBars(XSN_BTC, new Resolution(minutes = 1), from, to, 20)

      val expected = Bars(trade1.executedOn, trade1.price, trade1.price, trade1.price, trade1.price, 1)
      val expected2 = Bars(trade2.executedOn, trade2.price, trade2.price, trade2.price, trade2.price, 1)
      result.size must be(4)
      result.head must be(expected)
      result.tail.head must be(expected2)

    }

    "get bars by resolutions" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(3601 * 24)
      val instant3 = instant2.plusSeconds(3601 * 24)
      val instant4 = instant3.plusSeconds(3601 * 24)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      val listTrades = List(trade1, trade2, trade3, trade4)

      listTrades.foreach(repository.create)

      val from = instant1.minusSeconds(86400 * 180)
      val to = instant1.plusSeconds(86400 * 180)

      val resultByMonths = repository.getBars(XSN_BTC, new Resolution(months = 6), from, to, 20)
      val resultByWeeks = repository.getBars(XSN_BTC, new Resolution(weeks = 1), from, to, 20)
      val resultByDays = repository.getBars(XSN_BTC, new Resolution(days = 1), from, to, 20)
      val resultByMinutes = repository.getBars(XSN_BTC, new Resolution(minutes = 1), from, to, 20)

      resultByMonths.size must be(1)
      resultByWeeks.size must be(2)
      resultByDays.size must be(4)
      resultByMinutes.size must be(4)

    }
  }

  "getLastPrice" should {

    "return the last price" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(3601 * 24)
      val instant3 = instant2.plusSeconds(3601 * 24)
      val instant4 = instant3.plusSeconds(3601 * 24)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_LTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(LTC_BTC).copy(executedOn = instant4, id = id4)
      val listTrades = List(trade1, trade2, trade3, trade4)

      listTrades.foreach(repository.create)
      val result = repository.getLastPrice(XSN_BTC)
      val expected = TradingPairPrice(XSN_BTC, trade2.price, trade2.executedOn)
      result.value must be(expected)
    }

    "fail when there are not price for a given trading pair" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.ofEpochSecond(1577840400)
      val instant2 = instant1.plusSeconds(3601 * 24)
      val instant3 = instant2.plusSeconds(3601 * 24)
      val instant4 = instant3.plusSeconds(3601 * 24)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      val listTrades = List(trade1, trade2, trade3, trade4)

      listTrades.foreach(repository.create)
      val result = repository.getLastPrice(XSN_LTC)
      result must be(empty)
    }
  }

  "getVolume" should {
    "return the volume for a given trading pair for the last 24 hours" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(3600)
      val instant3 = instant2.minusSeconds(3600)
      val instant4 = instant3.minusSeconds(3601 * 24)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_LTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val btcRate1 = 0.1
      val usdRate1 = 0.2
      createCurrencyPrices(trade1.pair.secondary, btcRate1, usdRate1, instant1)

      val btcRate2 = 0.3
      val usdRate2 = 0.4
      createCurrencyPrices(trade2.pair.secondary, btcRate2, usdRate2, instant2)

      val btcRate3 = 0.5
      val usdRate3 = 0.6
      createCurrencyPrices(trade3.pair.secondary, btcRate3, usdRate3, instant3)

      val btcRate4 = 0.7
      val usdRate4 = 0.8
      createCurrencyPrices(trade4.pair.secondary, btcRate4, usdRate4, instant4)

      val result = repository.getVolume(XSN_BTC, lastDays = 1)
      val btcVolume = trade1.size * btcRate1 + trade2.size * btcRate2
      val usdVolume = trade1.size * usdRate1 + trade2.size * usdRate2
      val expected = TradingPairVolume(XSN_BTC, btcVolume, usdVolume)
      result must be(expected)
    }

    "return the volume for a given trading pair for the last week" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(3.days.toSeconds)
      val instant3 = instant2.minusSeconds(1.days.toSeconds)
      val instant4 = instant3.minusSeconds(10.days.toSeconds)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val btcRate = 0.1
      val usdRate = 0.2
      createCurrencyPrices(trade1.pair.secondary, btcRate, usdRate, instant4)

      val result = repository.getVolume(XSN_BTC, lastDays = 7)
      val btcVolume = (trade1.size + trade2.size + trade3.size) * btcRate
      val usdVolume = (trade1.size + trade2.size + trade3.size) * usdRate
      val expected = TradingPairVolume(XSN_BTC, btcVolume, usdVolume)
      result must be(expected)
    }

    "return the volume for a given trading pair for the last month" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(13.days.toSeconds)
      val instant3 = instant2.minusSeconds(10.days.toSeconds)
      val instant4 = instant3.minusSeconds(10.days.toSeconds)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_LTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_LTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_LTC).copy(executedOn = instant4, id = id4)
      val listTrades = List(trade1, trade2, trade3, trade4)

      listTrades.foreach(repository.create)

      val result = repository.getVolume(XSN_LTC, lastDays = 30)
      val expected = TradingPairVolume(XSN_LTC, Satoshis.Zero, Satoshis.Zero)
      result must be(expected)
    }

    "return the volume for a given trading pair for all the time" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(3.days.toSeconds)
      val instant3 = instant2.minusSeconds(1.days.toSeconds)
      val instant4 = instant3.minusSeconds(10.days.toSeconds)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val btcRate = 0.1
      val usdRate = 0.2
      createCurrencyPrices(trade1.pair.secondary, btcRate, usdRate, instant4)

      val result = repository.getVolume(XSN_BTC, lastDays = 0)
      val btcVolume = (trade1.size + trade2.size + trade3.size + trade4.size) * btcRate
      val usdVolume = (trade1.size + trade2.size + trade3.size + trade4.size) * usdRate
      val expected = TradingPairVolume(XSN_BTC, btcVolume, usdVolume)
      result must be(expected)
    }

    "return empty when there is no volume" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(1.hour.toSeconds)
      val instant3 = instant2.minusSeconds(1.hour.toSeconds)
      val instant4 = instant3.minusSeconds(2.days.toSeconds)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_LTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val result = repository.getVolume(tradingPair = LTC_BTC, lastDays = 1)
      val expected = TradingPairVolume.empty(LTC_BTC)
      result must be(expected)
    }
  }

  "getNumberOfTrades" should {
    "return the number of trades for a given trading pair" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(3600)
      val instant3 = instant2.minusSeconds(3600 * 24)
      val instant4 = instant3.minusSeconds(3601 * 24 * 7)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val resultByDay = repository.getNumberOfTrades(XSN_BTC, lastDays = 1)
      val expectedByDay = BigInt(2)
      resultByDay must be(expectedByDay)

      val resultByWeek = repository.getNumberOfTrades(XSN_BTC, lastDays = 7)
      val expectedByWeek = BigInt(3)
      resultByWeek must be(expectedByWeek)

      val resultByMonth = repository.getNumberOfTrades(XSN_BTC, lastDays = 30)
      val expectedByMonth = BigInt(4)
      resultByMonth must be(expectedByMonth)
    }

    "return 0 if there aren't any trades" in {
      val result = repository.getNumberOfTrades(XSN_BTC, lastDays = 1)
      result must be(BigInt(0))
    }

    "return the number of trades using lastDays = 0 to indicate all the registers" in {
      val List(id1, id2, id3, id4) = List.fill(4)(UUID.randomUUID()).map(Trade.Id.apply).sorted
      val instant1 = Instant.now()
      val instant2 = instant1.minusSeconds(3600)
      val instant3 = instant2.minusSeconds(3600 * 24)
      val instant4 = instant3.minusSeconds(3601 * 24 * 7)

      val trade1 = newTrade(XSN_BTC).copy(executedOn = instant1, id = id1)
      val trade2 = newTrade(XSN_BTC).copy(executedOn = instant2, id = id2)
      val trade3 = newTrade(XSN_BTC).copy(executedOn = instant3, id = id3)
      val trade4 = newTrade(XSN_BTC).copy(executedOn = instant4, id = id4)
      List(trade1, trade2, trade3, trade4).foreach(repository.create)

      val result = repository.getNumberOfTrades(XSN_BTC, lastDays = 0)
      val expected = BigInt(4)
      result must be(expected)
    }
  }
  private def newTrade(tradingPair: TradingPair): Trade = {
    Trade(
      id = Trade.Id(UUID.randomUUID()),
      pair = tradingPair,
      price = getSatoshis(Random.nextInt(100000)),
      size = getSatoshis(Random.nextInt(100000)),
      existingOrder = OrderId(UUID.randomUUID()),
      executingOrder = OrderId(UUID.randomUUID()),
      executingOrderSide = OrderSide.Sell,
      executedOn = Instant.now(),
      existingOrderFunds = getSatoshis(Random.nextInt(100000))
    )
  }

  private def createCurrencyPrices(
      currency: Currency,
      btcPrice: BigDecimal,
      usdPrice: BigDecimal,
      date: Instant
  ): Unit = {
    val pricesRepository = new CurrencyPricesPostgresRepository(database)
    val prices = CurrencyPrices(currency, btcPrice, usdPrice, date)

    pricesRepository.create(prices)
  }
}
