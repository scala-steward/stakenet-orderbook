package io.stakenet.orderbook.repositories.liquidityProviders

import java.time.Instant

import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.liquidityProviders.{
  LiquidityProviderId,
  LiquidityProviderLogId,
  LiquidityProviderLogType
}
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{ChannelIdentifier, Satoshis}
import javax.inject.Inject
import play.api.db.Database

class LiquidityProvidersPostgresRepository @Inject() (database: Database)
    extends LiquidityProvidersRepository.Blocking {

  override def createLiquidityProvider(
      liquidityProviderId: LiquidityProviderId,
      clientId: ClientId,
      tradingPair: TradingPair,
      principalChannelIdentifier: ChannelIdentifier,
      principalAmount: Satoshis,
      hubPrincipalChannelIdentifier: ChannelIdentifier,
      secondaryChannelIdentifier: ChannelIdentifier,
      secondaryAmount: Satoshis,
      hubSecondaryChannelIdentifier: ChannelIdentifier,
      createdAt: Instant
  ): Unit = {
    database.withTransaction { implicit conn =>
      LiquidityProvidersDAO.createLiquidityProvider(
        liquidityProviderId,
        clientId,
        tradingPair,
        principalChannelIdentifier,
        hubPrincipalChannelIdentifier,
        secondaryChannelIdentifier,
        hubSecondaryChannelIdentifier,
        createdAt
      )

      LiquidityProvidersDAO.createLiquidityProviderLog(
        LiquidityProviderLogId.random(),
        liquidityProviderId,
        principalAmount,
        tradingPair.principal,
        LiquidityProviderLogType.Joined,
        createdAt
      )

      LiquidityProvidersDAO.createLiquidityProviderLog(
        LiquidityProviderLogId.random(),
        liquidityProviderId,
        secondaryAmount,
        tradingPair.secondary,
        LiquidityProviderLogType.Joined,
        createdAt
      )
    }
  }
}
