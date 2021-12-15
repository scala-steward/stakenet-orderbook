package io.stakenet.orderbook.services

import com.google.inject.Inject
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.services.UsdConverter.{InvalidSatoshis, RateUnavailable}
import io.stakenet.orderbook.services.apis.PriceApi

import scala.concurrent.{ExecutionContext, Future}

class UsdConverter @Inject()(priceApi: PriceApi)(implicit ec: ExecutionContext) {

  def convert(amount: Satoshis, currency: Currency): Future[Either[UsdConverter.Error, BigDecimal]] = {
    priceApi.getUSDPrice(currency).map {
      case Some(price) =>
        Right((price * amount.toBigDecimal).setScale(8, BigDecimal.RoundingMode.DOWN))
      case None =>
        Left(RateUnavailable(currency))
    }
  }

  def convert(amount: BigDecimal, currency: Currency): Future[Either[UsdConverter.Error, Satoshis]] = {
    priceApi.getUSDPrice(currency).map {
      case Some(price) =>
        Satoshis
          .from((amount / price).setScale(8, BigDecimal.RoundingMode.DOWN))
          .map(Right(_))
          .getOrElse(Left(InvalidSatoshis()))
      case None =>
        Left(RateUnavailable(currency))
    }
  }
}

object UsdConverter {
  sealed trait Error

  case class RateUnavailable(currency: Currency) extends Error
  case class InvalidSatoshis() extends Error
}
