package io.stakenet.orderbook.lnd

import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Currency, Satoshis}
import lnrpc.rpc.{ChannelEventUpdate, OpenStatusUpdate}

import scala.concurrent.Future

trait MulticurrencyLndClient {
  def healthCheck(currency: Currency): Future[Unit]
  def lookupInvoice(currency: Currency, rHash: PaymentRHash): Future[lnrpc.rpc.Invoice]
  def decodePaymentRequest(paymentRequest: String, currency: Currency): Future[Either[String, PaymentRequestData]]
  def getBalance(currency: Currency): Future[Satoshis]
  def getPendingChannels(currency: Currency): Future[List[LndOutpoint]]
  def getClosedChannelPoints(currency: Currency): Future[List[LndOutpoint]]
  def getOpenChannels(currency: Currency): Future[List[OpenChannel]]
  def addInvoice(currency: Currency, amount: Satoshis, memo: String): Future[String]
  def getTransactions(currency: Currency): Future[List[lnrpc.rpc.Transaction]]
  def subscribeChannelEvents(currency: Currency, channelEventObserver: StreamObserver[ChannelEventUpdate]): Future[Unit]
  def getClientsHubBalance(currency: Currency): Future[HubLocalBalances]

  def openChannel(
      currency: Currency,
      nodePublicKey: Identifier.LndPublicKey,
      capacity: Satoshis,
      openChannelObserver: StreamObserver[OpenStatusUpdate],
      estimatedSatPerByte: Satoshis
  ): Future[Unit]
}
