package io.stakenet.orderbook.services

import akka.actor.ActorRef
import helpers.Helpers
import io.stakenet.orderbook.actors.orders.PeerOrder
import io.stakenet.orderbook.actors.peers.PeerTrade
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.lnd.LndHelper
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.{OrderSide, Trade, TradingPair}
import io.stakenet.orderbook.models.{Currency, MakerPaymentId, MakerPaymentStatus, Satoshis}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.repositories.makerPayments.MakerPaymentsRepository
import io.stakenet.orderbook.services.PaymentService.Error.PaymentFailed
import org.mockito.ArgumentMatchersSugar._
import org.mockito.Mockito
import org.mockito.MockitoSugar._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MakerPaymentServiceSpec extends AsyncWordSpec with Matchers {

  "payMaker" should {
    "succeed when payment is successful" in {
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val feesRepository = mock[FeesRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val lndHelper = mock[LndHelper]
      val service = getService(makerPaymentsRepository, feesRepository, clientsRepository, paymentService, lndHelper)

      val makerId = ClientId.random()
      val takerId = ClientId.random()
      val existingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Buy)
      val executingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Sell)
      val takerFee = Helpers.randomFee(executingOrder.feeCurrency)
      val makerPublicKey = Helpers.randomClientPublicKey(takerFee.currency)
      val trade = Trade.from(TradingPair.XSN_BTC)(executingOrder, existingOrder)
      val secondOrder = PeerOrder(takerId, mock[ActorRef], executingOrder)
      val peerTrade = PeerTrade(trade, secondOrder)
      val makerCommission = trade.sellOrderFee * 0.45

      when(feesRepository.find(executingOrder.id, executingOrder.feeCurrency)).thenReturn(Some(takerFee))
      when(clientsRepository.findPublicKey(makerId, takerFee.currency)).thenReturn(Some(makerPublicKey))
      when(lndHelper.getPublicKey(takerFee.currency)).thenReturn(Helpers.randomClientPublicKey().key)
      when(
        makerPaymentsRepository.createMakerPayment(
          any[MakerPaymentId],
          eqTo(trade.id),
          eqTo(makerId),
          eqTo(makerCommission),
          eqTo(takerFee.currency),
          eqTo(MakerPaymentStatus.Pending)
        )
      ).thenReturn(())
      when(paymentService.keySend(makerPublicKey.key, makerCommission, takerFee.currency)).thenReturn(
        Future.successful(Right(()))
      )

      service.payMaker(makerId, peerTrade).map { result =>
        verify(makerPaymentsRepository, Mockito.timeout(1000)).updateStatus(
          any[MakerPaymentId],
          eqTo(MakerPaymentStatus.Sent)
        )

        result mustBe Right(())
      }
    }

    "fail when maker has no public key for the taker's fee currency" in {
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val feesRepository = mock[FeesRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val lndHelper = mock[LndHelper]
      val service = getService(makerPaymentsRepository, feesRepository, clientsRepository, paymentService, lndHelper)

      val makerId = ClientId.random()
      val takerId = ClientId.random()
      val existingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Buy)
      val executingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Sell)
      val takerFee = Helpers.randomFee(executingOrder.feeCurrency)
      val trade = Trade.from(TradingPair.XSN_BTC)(executingOrder, existingOrder)
      val secondOrder = PeerOrder(takerId, mock[ActorRef], executingOrder)
      val peerTrade = PeerTrade(trade, secondOrder)

      when(feesRepository.find(executingOrder.id, executingOrder.feeCurrency)).thenReturn(Some(takerFee))
      when(clientsRepository.findPublicKey(makerId, takerFee.currency)).thenReturn(None)

      service.payMaker(makerId, peerTrade).map { result =>
        verify(makerPaymentsRepository, Mockito.timeout(1000).times(0)).createMakerPayment(
          any[MakerPaymentId],
          any[Trade.Id],
          any[ClientId],
          any[Satoshis],
          any[Currency],
          any[MakerPaymentStatus]
        )

        result mustBe Left(s"client $makerId has not public key for ${takerFee.currency}, skipping maker payment")
      }
    }

    "fail when taker did not pay a fee" in {
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val feesRepository = mock[FeesRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val lndHelper = mock[LndHelper]
      val service = getService(makerPaymentsRepository, feesRepository, clientsRepository, paymentService, lndHelper)

      val makerId = ClientId.random()
      val takerId = ClientId.random()
      val existingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Buy)
      val executingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Sell)
      val trade = Trade.from(TradingPair.XSN_BTC)(executingOrder, existingOrder)
      val secondOrder = PeerOrder(takerId, mock[ActorRef], executingOrder)
      val peerTrade = PeerTrade(trade, secondOrder)

      when(feesRepository.find(executingOrder.id, executingOrder.feeCurrency)).thenReturn(None)

      service.payMaker(makerId, peerTrade).map { result =>
        verify(makerPaymentsRepository, Mockito.timeout(1000).times(0)).createMakerPayment(
          any[MakerPaymentId],
          any[Trade.Id],
          any[ClientId],
          any[Satoshis],
          any[Currency],
          any[MakerPaymentStatus]
        )

        result mustBe Left(s"taker order ${executingOrder.id} did not pay a fee, skipping maker payment")
      }
    }

    "fail when keysend fails" in {
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val feesRepository = mock[FeesRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val lndHelper = mock[LndHelper]
      val service = getService(makerPaymentsRepository, feesRepository, clientsRepository, paymentService, lndHelper)

      val makerId = ClientId.random()
      val takerId = ClientId.random()
      val existingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Buy)
      val executingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Sell)
      val takerFee = Helpers.randomFee(executingOrder.feeCurrency)
      val makerPublicKey = Helpers.randomClientPublicKey(takerFee.currency)
      val trade = Trade.from(TradingPair.XSN_BTC)(executingOrder, existingOrder)
      val secondOrder = PeerOrder(takerId, mock[ActorRef], executingOrder)
      val peerTrade = PeerTrade(trade, secondOrder)
      val makerCommission = trade.sellOrderFee * 0.45

      when(feesRepository.find(executingOrder.id, executingOrder.feeCurrency)).thenReturn(Some(takerFee))
      when(clientsRepository.findPublicKey(makerId, takerFee.currency)).thenReturn(Some(makerPublicKey))
      when(lndHelper.getPublicKey(takerFee.currency)).thenReturn(Helpers.randomClientPublicKey().key)
      when(
        makerPaymentsRepository.createMakerPayment(
          any[MakerPaymentId],
          eqTo(trade.id),
          eqTo(makerId),
          eqTo(makerCommission),
          eqTo(takerFee.currency),
          eqTo(MakerPaymentStatus.Pending)
        )
      ).thenReturn(())
      when(paymentService.keySend(makerPublicKey.key, makerCommission, takerFee.currency)).thenReturn(
        Future.successful(Left(PaymentFailed("error")))
      )

      service.payMaker(makerId, peerTrade).map { result =>
        verify(makerPaymentsRepository, Mockito.timeout(1000)).updateStatus(
          any[MakerPaymentId],
          eqTo(MakerPaymentStatus.Failed)
        )

        result mustBe Left("error")
      }
    }

    "fails when keysend throws an exception" in {
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val feesRepository = mock[FeesRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val lndHelper = mock[LndHelper]
      val service = getService(makerPaymentsRepository, feesRepository, clientsRepository, paymentService, lndHelper)

      val makerId = ClientId.random()
      val takerId = ClientId.random()
      val existingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Buy)
      val executingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Sell)
      val takerFee = Helpers.randomFee(executingOrder.feeCurrency)
      val makerPublicKey = Helpers.randomClientPublicKey(takerFee.currency)
      val trade = Trade.from(TradingPair.XSN_BTC)(executingOrder, existingOrder)
      val secondOrder = PeerOrder(takerId, mock[ActorRef], executingOrder)
      val peerTrade = PeerTrade(trade, secondOrder)
      val makerCommission = trade.sellOrderFee * 0.45

      when(feesRepository.find(executingOrder.id, executingOrder.feeCurrency)).thenReturn(Some(takerFee))
      when(clientsRepository.findPublicKey(makerId, takerFee.currency)).thenReturn(Some(makerPublicKey))
      when(lndHelper.getPublicKey(takerFee.currency)).thenReturn(Helpers.randomClientPublicKey().key)
      when(
        makerPaymentsRepository.createMakerPayment(
          any[MakerPaymentId],
          eqTo(trade.id),
          eqTo(makerId),
          eqTo(makerCommission),
          eqTo(takerFee.currency),
          eqTo(MakerPaymentStatus.Pending)
        )
      ).thenReturn(())
      when(paymentService.keySend(makerPublicKey.key, makerCommission, takerFee.currency)).thenReturn(
        Future.failed(new RuntimeException("Unexpected error"))
      )

      service.payMaker(makerId, peerTrade).map { result =>
        verify(makerPaymentsRepository, Mockito.timeout(1000)).updateStatus(
          any[MakerPaymentId],
          eqTo(MakerPaymentStatus.Failed)
        )

        result mustBe Left("Unexpected error")
      }
    }

    "skip self payment" in {
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val feesRepository = mock[FeesRepository.Blocking]
      val clientsRepository = mock[ClientsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val lndHelper = mock[LndHelper]
      val service = getService(makerPaymentsRepository, feesRepository, clientsRepository, paymentService, lndHelper)

      val makerId = ClientId.random()
      val takerId = ClientId.random()
      val existingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Buy)
      val executingOrder = Helpers.randomOrder(TradingPair.XSN_BTC, OrderSide.Sell)
      val takerFee = Helpers.randomFee(executingOrder.feeCurrency)
      val makerPublicKey = Helpers.randomClientPublicKey(takerFee.currency)
      val trade = Trade.from(TradingPair.XSN_BTC)(executingOrder, existingOrder)
      val secondOrder = PeerOrder(takerId, mock[ActorRef], executingOrder)
      val peerTrade = PeerTrade(trade, secondOrder)

      when(feesRepository.find(executingOrder.id, executingOrder.feeCurrency)).thenReturn(Some(takerFee))
      when(clientsRepository.findPublicKey(makerId, takerFee.currency)).thenReturn(Some(makerPublicKey))
      when(lndHelper.getPublicKey(takerFee.currency)).thenReturn(makerPublicKey.key)

      service.payMaker(makerId, peerTrade).map { result =>
        verify(makerPaymentsRepository, Mockito.timeout(1000).times(0)).createMakerPayment(
          any[MakerPaymentId],
          any[Trade.Id],
          any[ClientId],
          any[Satoshis],
          any[Currency],
          any[MakerPaymentStatus]
        )

        result mustBe Left("skipping self payment")
      }
    }
  }

  private def getService(
      makerPaymentsRepository: MakerPaymentsRepository.Blocking,
      feesRepository: FeesRepository.Blocking,
      clientsRepository: ClientsRepository.Blocking,
      paymentService: PaymentService,
      lndHelper: LndHelper
  ): MakerPaymentService = {
    new MakerPaymentService.Impl(
      new MakerPaymentsRepository.FutureImpl(makerPaymentsRepository)(Executors.databaseEC),
      new FeesRepository.FutureImpl(feesRepository)(Executors.databaseEC),
      new ClientsRepository.FutureImpl(clientsRepository)(Executors.databaseEC),
      paymentService,
      lndHelper
    )
  }
}
