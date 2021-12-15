package io.stakenet.orderbook.services.validators

import java.time.Instant

import akka.actor.ActorSystem
import akka.testkit.TestKit
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.models.{OrderId, Satoshis, TradingPairPrice}
import io.stakenet.orderbook.models.trading.{OrderSide, TradingOrder}
import io.stakenet.orderbook.models.trading.TradingPair.XSN_LTC
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.{CurrencyConverter, ExplorerService, UsdConverter}
import io.stakenet.orderbook.services.apis.PriceApi
import org.mockito.MockitoSugar.mock
import org.scalatest.wordspec.AsyncWordSpecLike
import org.scalatest.matchers.must.Matchers._
import org.mockito.MockitoSugar.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration._

class OrderValidatorSpec
    extends TestKit(ActorSystem("OrderValidatorSpec"))
    with AsyncWordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "validate" should {
    "approve valid limit sell order" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.from,
          price = pair.sellPriceInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "reject sell limit order when funds are less than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.from - Satoshis.One,
          price = pair.sellPriceInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.sellFundsInterval))
        )
      }
    }

    "reject sell limit order when funds are more than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.to + Satoshis.One,
          price = pair.sellPriceInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.sellFundsInterval))
        )
      }
    }

    "reject sell limit order when price is less than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.from,
          price = pair.sellPriceInterval.from - Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidPrice(order.asLimitOrder.get.details.price, order.pair.sellPriceInterval))
        )
      }
    }

    "reject sell limit order when price is more than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.from,
          price = pair.sellPriceInterval.to + Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidPrice(order.asLimitOrder.get.details.price, order.pair.sellPriceInterval))
        )
      }
    }

    "approve valid limit buy order" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.from,
          price = pair.buyPriceInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "reject buy limit order when funds are less than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.from - Satoshis.One,
          price = pair.buyPriceInterval.from
        )
      )

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.buyFundsInterval))
        )
      }
    }

    "reject buy limit order when funds are more than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.to + Satoshis.One,
          price = pair.buyPriceInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.buyFundsInterval))
        )
      }
    }

    "reject buy limit order when price is less than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.from,
          price = pair.buyPriceInterval.from - Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidPrice(order.asLimitOrder.get.details.price, order.pair.buyPriceInterval))
        )
      }
    }

    "reject buy limit order when price is more than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.from,
          price = pair.buyPriceInterval.to + Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidPrice(order.asLimitOrder.get.details.price, order.pair.buyPriceInterval))
        )
      }
    }

    "approve valid market sell order" in {
      val explorerService = mock[ExplorerService]
      val tradesRepository = mock[TradesRepository.Blocking]
      val validator = getOrderValidator(explorerService, tradesRepository)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))
      when(tradesRepository.getLastPrice(order.pair)).thenReturn(None)

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "reject sell market order when funds are less than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.from - Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.sellFundsInterval))
        )
      }
    }

    "reject sell market order when funds are more than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Sell,
          OrderId.random(),
          funds = pair.sellFundsInterval.to + Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.sellFundsInterval))
        )
      }
    }

    "approve valid market buy order" in {
      val explorerService = mock[ExplorerService]
      val tradesRepository = mock[TradesRepository.Blocking]
      val validator = getOrderValidator(explorerService, tradesRepository)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.sellFundsInterval.from
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))
      when(tradesRepository.getLastPrice(order.pair)).thenReturn(None)

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "reject buy market order when funds are less than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.from - Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.buyFundsInterval))
        )
      }
    }

    "reject buy market order when funds are more than the accepted range" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Buy,
          OrderId.random(),
          funds = pair.buyFundsInterval.to + Satoshis.One
        )
      )

      when(explorerService.getUSDPrice(order.pair.principal)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.pair.secondary)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(
          Left(OrderValidator.Error.InvalidFunds(order.value.funds, order.pair.buyFundsInterval))
        )
      }
    }

    "reject limit order when selling and buying value its too high" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = Satoshis.from(BigDecimal("1000.00000001")).value,
          price = pair.sellPriceInterval.to
        )
      )

      when(explorerService.getUSDPrice(order.value.sellingCurrency)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.value.buyingCurrency)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(Left(OrderValidator.Error.MaxValueExceeded()))
      }
    }

    "approve limit order when only buying value its too high" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = Satoshis.from(BigDecimal(1)).value,
          price = Satoshis.from(BigDecimal("1000.00000001")).value
        )
      )

      when(explorerService.getUSDPrice(order.value.sellingCurrency)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.value.buyingCurrency)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "approve limit order when only selling value its too high" in {
      val explorerService = mock[ExplorerService]
      val validator = getOrderValidator(explorerService)
      val pair = XSN_LTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = Satoshis.from(BigDecimal(1)).value,
          price = Satoshis.from(BigDecimal(1000)).value
        )
      )

      when(explorerService.getUSDPrice(order.value.sellingCurrency)).thenReturn(Future.successful(Right(1001)))
      when(explorerService.getUSDPrice(order.value.buyingCurrency)).thenReturn(Future.successful(Right(1)))

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "reject market order when selling and buying value its too high" in {
      val explorerService = mock[ExplorerService]
      val tradesRepository = mock[TradesRepository.Blocking]
      val validator = getOrderValidator(explorerService, tradesRepository)
      val pair = XSN_LTC
      val lastPrice = TradingPairPrice(pair, Satoshis.from(BigDecimal(1)).value, Instant.now)
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Sell,
          OrderId.random(),
          funds = Satoshis.from(BigDecimal("1000.00000001")).value
        )
      )

      when(explorerService.getUSDPrice(order.value.sellingCurrency)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.value.buyingCurrency)).thenReturn(Future.successful(Right(1)))
      when(tradesRepository.getLastPrice(order.pair)).thenReturn(Some(lastPrice))

      validator.validate(order).map { result =>
        result must be(Left(OrderValidator.Error.MaxValueExceeded()))
      }
    }

    "approve market order when only buying value its too high" in {
      val explorerService = mock[ExplorerService]
      val tradesRepository = mock[TradesRepository.Blocking]
      val validator = getOrderValidator(explorerService, tradesRepository)
      val pair = XSN_LTC
      val lastPrice = TradingPairPrice(pair, Satoshis.from(BigDecimal(0.2)).value, Instant.now)
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Sell,
          OrderId.random(),
          funds = Satoshis.from(BigDecimal("1000.00000001")).value
        )
      )

      when(explorerService.getUSDPrice(order.value.sellingCurrency)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.value.buyingCurrency)).thenReturn(Future.successful(Right(1)))
      when(tradesRepository.getLastPrice(order.pair)).thenReturn(Some(lastPrice))

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }

    "approve market order when only selling value its too high" in {
      val explorerService = mock[ExplorerService]
      val tradesRepository = mock[TradesRepository.Blocking]
      val validator = getOrderValidator(explorerService, tradesRepository)
      val pair = XSN_LTC
      val lastPrice = TradingPairPrice(pair, Satoshis.from(BigDecimal(2)).value, Instant.now)
      val order = TradingOrder(pair)(
        pair.Order.market(
          OrderSide.Sell,
          OrderId.random(),
          funds = Satoshis.from(BigDecimal(600)).value
        )
      )

      when(explorerService.getUSDPrice(order.value.sellingCurrency)).thenReturn(Future.successful(Right(1)))
      when(explorerService.getUSDPrice(order.value.buyingCurrency)).thenReturn(Future.successful(Right(1)))
      when(tradesRepository.getLastPrice(order.pair)).thenReturn(Some(lastPrice))

      validator.validate(order).map { result =>
        result must be(Right(()))
      }
    }
  }

  private def getOrderValidator(
      explorerService: ExplorerService,
      tradesRepository: TradesRepository.Blocking = mock[TradesRepository.Blocking]
  ): OrderValidator = {
    val retryConfig = RetryConfig(1.millisecond, 1.millisecond)
    val tradesRepositoryAsync = new TradesRepository.FutureImpl(tradesRepository)(Executors.databaseEC)
    val priceApi = new PriceApi(
      tradesRepositoryAsync,
      explorerService,
      retryConfig
    )(Executors.globalEC, system.scheduler)

    val usdConverter = new UsdConverter(priceApi)(Executors.globalEC)
    val currencyConverter = new CurrencyConverter(priceApi)(Executors.globalEC)

    new OrderValidator(usdConverter, currencyConverter)(Executors.globalEC)
  }
}
