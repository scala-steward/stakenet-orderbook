package io.stakenet.orderbook.repositories.liquidityProviders

import java.time.Instant

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.liquidityProviders.LiquidityProviderId
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{ChannelIdentifier, Satoshis}
import javax.inject.Inject

import scala.concurrent.Future

trait LiquidityProvidersRepository[F[_]] {

  def createLiquidityProvider(
      id: LiquidityProviderId,
      clientId: ClientId,
      tradingPair: TradingPair,
      principalChannelOutpoint: ChannelIdentifier,
      principalAmount: Satoshis,
      hubPrincipalChannelOutpoint: ChannelIdentifier,
      secondaryChannelOutpoint: ChannelIdentifier,
      secondaryAmount: Satoshis,
      hubSecondaryChannelOutpoint: ChannelIdentifier,
      createdAt: Instant
  ): F[Unit]
}

object LiquidityProvidersRepository {

  type Id[T] = T
  trait Blocking extends LiquidityProvidersRepository[Id]

  class FutureImpl @Inject() (blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends LiquidityProvidersRepository[Future] {

    override def createLiquidityProvider(
        id: LiquidityProviderId,
        clientId: ClientId,
        tradingPair: TradingPair,
        principalChannelOutpoint: ChannelIdentifier,
        principalAmount: Satoshis,
        hubPrincipalChannelOutpoint: ChannelIdentifier,
        secondaryChannelOutpoint: ChannelIdentifier,
        secondaryAmount: Satoshis,
        hubSecondaryChannelOutpoint: ChannelIdentifier,
        createdAt: Instant
    ): Future[Unit] = Future {
      blocking.createLiquidityProvider(
        id,
        clientId,
        tradingPair,
        principalChannelOutpoint,
        principalAmount,
        hubPrincipalChannelOutpoint,
        secondaryChannelOutpoint,
        secondaryAmount,
        hubSecondaryChannelOutpoint,
        createdAt
      )
    }
  }
}
