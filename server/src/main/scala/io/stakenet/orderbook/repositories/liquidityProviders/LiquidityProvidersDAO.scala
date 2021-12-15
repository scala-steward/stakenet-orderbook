package io.stakenet.orderbook.repositories.liquidityProviders

import java.sql.Connection
import java.time.Instant

import anorm._
import io.stakenet.orderbook.models.clients._
import io.stakenet.orderbook.models.liquidityProviders.{
  LiquidityProviderId,
  LiquidityProviderLogId,
  LiquidityProviderLogType
}
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{ChannelIdentifier, Currency, Satoshis}
import org.postgresql.util.{PSQLException, PSQLState}

private[liquidityProviders] object LiquidityProvidersDAO {

  private object Constraints {
    val liquidityProvidersPK = "liquidity_providers_pk"
    val liquidityProvidersClientIdFK = "liquidity_providers_client_id_fk"
    val liquidityProviderLogsPK = "liquidity_provider_logs_pk"
    val liquidityProviderLogsLiquidityProviderFK = "liquidity_provider_logs_liquidity_provider_fk"
  }

  def createLiquidityProvider(
      id: LiquidityProviderId,
      clientId: ClientId,
      tradingPair: TradingPair,
      principalChannelOutpoint: ChannelIdentifier,
      hubPrincipalChannelOutpoint: ChannelIdentifier,
      secondaryChannelOutpoint: ChannelIdentifier,
      hubSecondaryChannelOutpoint: ChannelIdentifier,
      createdAt: Instant
  )(implicit conn: Connection): Unit = {
    try {
      SQL"""
         INSERT INTO liquidity_providers(
           liquidity_provider_id,
           client_id,
           trading_pair,
           principal_channel_identifier,
           hub_principal_channel_identifier,
           secondary_channel_identifier,
           hub_secondary_channel_identifier,
           created_at
         ) VALUES (
           ${id.toString}::UUID,
           ${clientId.toString}::UUID,
           ${tradingPair.toString}::TRADING_PAIR,
           ${principalChannelOutpoint.toString},
           ${hubPrincipalChannelOutpoint.toString},
           ${secondaryChannelOutpoint.toString},
           ${hubSecondaryChannelOutpoint.toString},
           $createdAt
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.liquidityProvidersPK) =>
        throw new PSQLException(s"liquidity provider $id already exists", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.liquidityProvidersClientIdFK) =>
        throw new PSQLException(s"client $clientId not found", PSQLState.DATA_ERROR)
    }
  }

  def createLiquidityProviderLog(
      id: LiquidityProviderLogId,
      liquidityProviderId: LiquidityProviderId,
      amount: Satoshis,
      currency: Currency,
      logType: LiquidityProviderLogType,
      createdAt: Instant
  )(implicit conn: Connection): Unit = {
    try {
      SQL"""
         INSERT INTO liquidity_provider_logs(
           liquidity_provider_log_id,
           liquidity_provider_id,
           amount,
           currency,
           liquidity_provider_log_type,
           created_at
         ) VALUES (
           ${id.toString}::UUID,
           ${liquidityProviderId.toString}::UUID,
           ${amount.toString}::SATOSHIS_TYPE,
           ${currency.toString}::CURRENCY_TYPE,
           ${logType.entryName}::LIQUIDITY_POOL_LOG_TYPE,
           $createdAt
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.liquidityProviderLogsPK) =>
        throw new PSQLException(s"liquidity provider log $id already exists", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.liquidityProviderLogsLiquidityProviderFK) =>
        throw new PSQLException(s"liquidity provider $liquidityProviderId not found", PSQLState.DATA_ERROR)
    }
  }

  private def isConstraintError(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
