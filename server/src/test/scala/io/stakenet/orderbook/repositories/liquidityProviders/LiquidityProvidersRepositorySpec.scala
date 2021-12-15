package io.stakenet.orderbook.repositories.liquidityProviders

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.liquidityProviders.LiquidityProviderId
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter

class LiquidityProvidersRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new LiquidityProvidersPostgresRepository(database)

  "createLiquidityProvider" should {
    "create a liquidity provider" in {
      val id = LiquidityProviderId.random()
      val clientId = createClient()
      val tradingPair = TradingPair.XSN_BTC
      val principalChannelOutput = Helpers.randomOutpoint()
      val hubPrincipalChannelOutput = Helpers.randomOutpoint()
      val secondaryChannelOutput = Helpers.randomOutpoint()
      val hubSecondaryChannelOutput = Helpers.randomOutpoint()
      val createdAt = Instant.now()

      repository.createLiquidityProvider(
        id,
        clientId,
        tradingPair,
        principalChannelOutput,
        Satoshis.One,
        hubPrincipalChannelOutput,
        secondaryChannelOutput,
        Satoshis.One,
        hubSecondaryChannelOutput,
        createdAt
      )

      succeed
    }

    "fail on duplicated liquidity provider" in {
      val id = LiquidityProviderId.random()
      val clientId1 = createClient()
      val clientId2 = createClient()
      val tradingPair = TradingPair.XSN_BTC
      val principalChannelOutput = Helpers.randomOutpoint()
      val hubPrincipalChannelOutput = Helpers.randomOutpoint()
      val secondaryChannelOutput = Helpers.randomOutpoint()
      val hubSecondaryChannelOutput = Helpers.randomOutpoint()
      val createdAt = Instant.now()

      repository.createLiquidityProvider(
        id,
        clientId1,
        tradingPair,
        principalChannelOutput,
        Satoshis.One,
        hubPrincipalChannelOutput,
        secondaryChannelOutput,
        Satoshis.One,
        hubSecondaryChannelOutput,
        createdAt
      )

      val error = intercept[PSQLException] {
        repository.createLiquidityProvider(
          id,
          clientId2,
          tradingPair,
          principalChannelOutput,
          Satoshis.One,
          hubPrincipalChannelOutput,
          secondaryChannelOutput,
          Satoshis.One,
          hubSecondaryChannelOutput,
          createdAt
        )
      }

      error.getMessage mustBe s"liquidity provider $id already exists"
    }

    "fail on missing client" in {
      val id = LiquidityProviderId.random()
      val clientId = ClientId.random()
      val tradingPair = TradingPair.XSN_BTC
      val principalChannelOutput = Helpers.randomOutpoint()
      val hubPrincipalChannelOutput = Helpers.randomOutpoint()
      val secondaryChannelOutput = Helpers.randomOutpoint()
      val hubSecondaryChannelOutput = Helpers.randomOutpoint()
      val createdAt = Instant.now()

      val error = intercept[PSQLException] {
        repository.createLiquidityProvider(
          id,
          clientId,
          tradingPair,
          principalChannelOutput,
          Satoshis.One,
          hubPrincipalChannelOutput,
          secondaryChannelOutput,
          Satoshis.One,
          hubSecondaryChannelOutput,
          createdAt
        )
      }

      error.getMessage mustBe s"client $clientId not found"
    }
  }

  private def createClient(): ClientId = {
    val clientsRepository = new ClientsPostgresRepository(database)

    clientsRepository.createWalletClient(Helpers.randomWalletId())
  }
}
