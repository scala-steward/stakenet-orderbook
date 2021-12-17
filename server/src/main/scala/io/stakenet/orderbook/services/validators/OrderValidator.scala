package io.stakenet.orderbook.services.validators

import com.google.inject.Inject
import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.Satoshis.InclusiveInterval
import io.stakenet.orderbook.models.trading.{OrderSide, TradingOrder, TradingPair}
import io.stakenet.orderbook.services.{CurrencyConverter, UsdConverter}
import io.stakenet.orderbook.services.validators.OrderValidator.Error._
import io.stakenet.orderbook.utils.Extensions._

import scala.concurrent.{ExecutionContext, Future}

class OrderValidator @Inject() (usdConverter: UsdConverter, currencyConverter: CurrencyConverter)(implicit
    ec: ExecutionContext
) {

  def validate(tradingOrder: TradingOrder): Future[Either[OrderValidator.Error, Unit]] = {
    tradingOrder
      .fold(
        limitOrder => {
          for {
            _ <- validateFunds(tradingOrder.pair)(limitOrder).toFutureEither()
            _ <- validatePrice(tradingOrder.pair)(limitOrder).toFutureEither()
            _ <- validateOrderValue(tradingOrder).toFutureEither()
          } yield ()
        },
        marketOrder => {
          for {
            _ <- validateFunds(tradingOrder.pair)(marketOrder).toFutureEither()
            _ <- validateOrderValue(tradingOrder).toFutureEither()
          } yield ()
        }
      )
      .toFuture
  }

  private def validateFunds(pair: TradingPair)(order: pair.Order): Either[OrderValidator.Error, Unit] = {
    order.side match {
      case OrderSide.Buy =>
        Either.cond(pair.buyFundsInterval.contains(order.funds), (), InvalidFunds(order.funds, pair.buyFundsInterval))
      case OrderSide.Sell =>
        Either.cond(pair.sellFundsInterval.contains(order.funds), (), InvalidFunds(order.funds, pair.sellFundsInterval))
    }
  }

  private def validatePrice(pair: TradingPair)(order: pair.LimitOrder): Either[OrderValidator.Error, Unit] = {
    order.side match {
      case OrderSide.Buy =>
        Either.cond(
          pair.buyPriceInterval.contains(order.details.price),
          (),
          InvalidPrice(order.details.price, pair.buyPriceInterval)
        )
      case OrderSide.Sell =>
        Either.cond(
          pair.sellPriceInterval.contains(order.details.price),
          (),
          OrderValidator.Error.InvalidPrice(order.details.price, pair.sellPriceInterval)
        )
    }
  }

  private def validateOrderValue(tradingOrder: TradingOrder): Future[Either[OrderValidator.Error, Unit]] = {
    val maxOrderValueUsd = BigDecimal(1000)

    val valid = for {
      maxSellingValue <- usdConverter.convert(maxOrderValueUsd, tradingOrder.value.sellingCurrency).toFutureEither()
      maxBuyingValue <- usdConverter.convert(maxOrderValueUsd, tradingOrder.value.buyingCurrency).toFutureEither()
      sellingValue = tradingOrder.value.funds
      buyingValue <- getOrderBuyingValue(tradingOrder).map(Right(_)).toFutureEither()

      validSellingValue = sellingValue <= maxSellingValue
      validBuyingValue = buyingValue <= maxBuyingValue
    } yield validSellingValue || validBuyingValue

    valid.toFuture.map {
      case Right(true) => Right(())
      case Right(false) => Left(MaxValueExceeded())
      case Left(_) => Left(CouldNotVerifyOrderValue())
    }
  }

  private def getOrderBuyingValue(tradingOrder: TradingOrder): Future[Satoshis] = {
    tradingOrder.fold(
      limitOrder => {
        Future.successful(
          currencyConverter.convert(
            amount = limitOrder.funds,
            currency = limitOrder.sellingCurrency,
            target = limitOrder.buyingCurrency,
            price = limitOrder.details.price
          )
        )
      },
      marketOrder => {
        currencyConverter.convert(marketOrder.funds, marketOrder.sellingCurrency, marketOrder.buyingCurrency)
      }
    )
  }
}

object OrderValidator {
  sealed trait Error

  object Error {
    final case class InvalidFunds(got: Satoshis, expected: InclusiveInterval) extends OrderValidator.Error
    final case class InvalidPrice(got: Satoshis, expected: InclusiveInterval) extends OrderValidator.Error
    final case class MaxValueExceeded() extends OrderValidator.Error
    final case class CouldNotVerifyOrderValue() extends OrderValidator.Error
  }
}
