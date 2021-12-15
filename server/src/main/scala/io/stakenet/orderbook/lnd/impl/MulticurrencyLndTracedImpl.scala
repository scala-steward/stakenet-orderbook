package io.stakenet.orderbook.lnd.impl

import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Currency, Satoshis}
import javax.inject.Inject
import lnrpc.rpc.{ChannelEventUpdate, OpenStatusUpdate, Transaction}

import scala.concurrent.{ExecutionContext, Future}

class MulticurrencyLndTracedImpl @Inject()(impl: MulticurrencyLndDefaultImpl)(implicit ec: ExecutionContext)
    extends MulticurrencyLndClient {

  import io.stakenet.orderbook.lnd.LndTraceHelper._

  override def healthCheck(currency: Currency): Future[Unit] = trace("getVersion", currency) {
    impl.healthCheck(currency)
  }

  override def lookupInvoice(currency: Currency, rHash: PaymentRHash): Future[lnrpc.rpc.Invoice] = {
    trace("lookupInvoice", currency) {
      impl.lookupInvoice(currency, rHash)
    }
  }

  override def decodePaymentRequest(
      paymentRequest: String,
      currency: Currency
  ): Future[Either[String, PaymentRequestData]] = {
    trace("decodePayReq", currency) {
      impl.decodePaymentRequest(paymentRequest, currency)
    }
  }

  override def getBalance(currency: Currency): Future[Satoshis] = {
    trace("walletBalance", currency) {
      impl.getBalance(currency)
    }
  }

  override def getPendingChannels(currency: Currency): Future[List[LndOutpoint]] = trace("pendingchannels", currency) {
    impl.getPendingChannels(currency)
  }

  override def getClosedChannelPoints(currency: Currency): Future[List[LndOutpoint]] = {
    trace("closedChannels", currency) {
      impl.getClosedChannelPoints(currency)
    }
  }

  override def getOpenChannels(currency: Currency): Future[List[OpenChannel]] = trace("listChannels", currency) {
    impl.getOpenChannels(currency)
  }

  override def addInvoice(currency: Currency, amount: Satoshis, memo: String): Future[String] = {
    trace("addInvoice", currency) {
      impl.addInvoice(currency, amount, memo)
    }
  }

  override def getTransactions(currency: Currency): Future[List[Transaction]] = {
    trace("getTransactions", currency) {
      impl.getTransactions(currency)
    }
  }

  override def subscribeChannelEvents(
      currency: Currency,
      channelEventObserver: StreamObserver[ChannelEventUpdate]
  ): Future[Unit] = {
    trace("subscribeChannelEvents", currency) {
      impl.subscribeChannelEvents(currency, channelEventObserver)
    }
  }

  override def openChannel(
      currency: Currency,
      nodePublicKey: Identifier.LndPublicKey,
      capacity: Satoshis,
      openChannelObserver: StreamObserver[OpenStatusUpdate],
      estimatedSatPerByte: Satoshis
  ): Future[Unit] = {
    trace("openChannel", currency) {
      impl.openChannel(currency, nodePublicKey, capacity, openChannelObserver, estimatedSatPerByte)
    }
  }

  override def getClientsHubBalance(currency: Currency): Future[HubLocalBalances] = {
    trace("listChannels", currency) {
      impl.getClientsHubBalance(currency)
    }
  }
}
