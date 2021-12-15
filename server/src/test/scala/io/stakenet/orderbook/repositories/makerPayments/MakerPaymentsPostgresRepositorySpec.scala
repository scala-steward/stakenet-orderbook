package io.stakenet.orderbook.repositories.makerPayments

import java.util.UUID

import helpers.Helpers
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.{Trade, TradingPair}
import io.stakenet.orderbook.models.{Currency, MakerPaymentId, MakerPaymentStatus, Satoshis}
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.trades.{TradesDAO, TradesPostgresRepository}
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter

class MakerPaymentsPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new MakerPaymentsPostgresRepository(database)

  "createMakerPayment" should {
    "create a maker payment" in {
      val tradeId = createTrade()
      val clientId = createClient()

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Pending
      )

      succeed
    }

    "fail when client does not exist" in {
      val tradeId = createTrade()
      val clientId = ClientId.random()

      val error = intercept[PSQLException] {
        repository.createMakerPayment(
          MakerPaymentId.random(),
          tradeId,
          clientId,
          Satoshis.One,
          Currency.XSN,
          MakerPaymentStatus.Pending
        )
      }

      error.getMessage mustBe s"client $clientId not found"
    }

    "fail when trade does not exist" in {
      val tradeId = Trade.Id(UUID.randomUUID())
      val clientId = createClient()

      val error = intercept[PSQLException] {
        repository.createMakerPayment(
          MakerPaymentId.random(),
          tradeId,
          clientId,
          Satoshis.One,
          Currency.XSN,
          MakerPaymentStatus.Pending
        )
      }

      error.getMessage mustBe s"trade $tradeId not found"
    }
  }

  "getFailedPayments" should {
    "get failed payments for a client" in {
      val tradeId = createTrade()
      val clientId = createClient()

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Pending
      )

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Sent
      )

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Failed
      )

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Failed
      )

      val result = repository.getFailedPayments(clientId)

      result.length mustBe 2
      result.forall(_.status == MakerPaymentStatus.Failed) mustBe true
    }

    "get an empty list when client has not failed payments" in {
      val tradeId = createTrade()
      val clientId = createClient()

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Pending
      )

      repository.createMakerPayment(
        MakerPaymentId.random(),
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Sent
      )

      val result = repository.getFailedPayments(clientId)

      result.isEmpty mustBe true
    }

    "get an empty list when client does not exist" in {
      val result = repository.getFailedPayments(ClientId.random())

      result.isEmpty mustBe true
    }
  }

  "updateStatus" should {
    "update the status of a payment" in {
      val tradeId = createTrade()
      val clientId = createClient()
      val id = MakerPaymentId.random()

      repository.createMakerPayment(
        id,
        tradeId,
        clientId,
        Satoshis.One,
        Currency.XSN,
        MakerPaymentStatus.Failed
      )

      val result1 = repository.getFailedPayments(clientId)
      result1.length mustBe 1

      repository.updateStatus(id, MakerPaymentStatus.Sent)

      val result2 = repository.getFailedPayments(clientId)
      result2.length mustBe 0
    }

    "do nothing when payment does not exist" in {
      repository.updateStatus(MakerPaymentId.random(), MakerPaymentStatus.Sent)

      succeed
    }
  }

  private def createClient(): ClientId = {
    val clientsRepository = new ClientsPostgresRepository(database)

    clientsRepository.createWalletClient(Helpers.randomWalletId())
  }

  private def createTrade(): Trade.Id = {
    val tradesRepository = new TradesPostgresRepository(database, new TradesDAO)
    val trade = Helpers.randomTrade(TradingPair.XSN_BTC)

    tradesRepository.create(trade)

    trade.id
  }
}
