package io.stakenet.orderbook.lnd.impl

import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.lnd.LndHelper.GetBalanceError
import io.stakenet.orderbook.lnd.{LightningClientBuilder, MulticurrencyLndClient}
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Currency, Satoshis}
import javax.inject.Inject
import lnrpc.rpc.{ChannelEventUpdate, ListChannelsRequest, OpenStatusUpdate, Transaction}

import scala.concurrent.{ExecutionContext, Future}

class MulticurrencyLndDefaultImpl @Inject()(clientBuilder: LightningClientBuilder)(implicit ec: ExecutionContext)
    extends MulticurrencyLndClient {

  override def healthCheck(currency: Currency): Future[Unit] = {
    clientBuilder
      .getLndVersioner(currency)
      .getVersion(verrpc.verrpc.VersionRequest())
      .map(_ => ())
  }

  override def lookupInvoice(currency: Currency, rHash: PaymentRHash): Future[lnrpc.rpc.Invoice] = {
    val invoice = clientBuilder
      .getLnd(currency)
      .lookupInvoice(lnrpc.rpc.PaymentHash(rHash = ByteString.copyFrom(rHash.value.toArray)))

    invoice
  }

  override def decodePaymentRequest(
      paymentRequest: String,
      currency: Currency
  ): Future[Either[String, PaymentRequestData]] = {
    clientBuilder
      .getLnd(currency)
      .decodePayReq(lnrpc.rpc.PayReqString(paymentRequest))
      .map { response =>
        currency
          .satoshis(BigInt(response.numSatoshis))
          .map(amount => PaymentRequestData(amount))
          .toRight("Payment request, invalid numSatoshis")
      }
  }

  override def getBalance(currency: Currency): Future[Satoshis] = {
    val lnd = clientBuilder.getLnd(currency)

    lnd.walletBalance(lnrpc.rpc.WalletBalanceRequest()).map { response =>
      currency
        .satoshis(BigInt(response.confirmedBalance))
        .getOrElse(throw new GetBalanceError.InvalidBalance(response.confirmedBalance))
    }
  }

  override def getPendingChannels(currency: Currency): Future[List[LndOutpoint]] = {
    clientBuilder
      .getLnd(currency)
      .pendingChannels(lnrpc.rpc.PendingChannelsRequest())
      .map { response =>
        response.waitingCloseChannels.flatMap { waitingCloseChannel =>
          waitingCloseChannel.channel.flatMap(x => LndOutpoint.untrusted(x.channelPoint))
        }.toList
      }
  }

  override def getClosedChannelPoints(currency: Currency): Future[List[LndOutpoint]] = {
    clientBuilder
      .getLnd(currency)
      .closedChannels(lnrpc.rpc.ClosedChannelsRequest())
      .map { response =>
        response.channels.flatMap(channel => LndOutpoint.untrusted(channel.channelPoint)).toList
      }
  }

  override def getOpenChannels(currency: Currency): Future[List[OpenChannel]] = {
    clientBuilder
      .getLnd(currency)
      .listChannels(lnrpc.rpc.ListChannelsRequest())
      .map { response =>
        response.channels.flatMap { channel =>
          LndOutpoint.untrusted(channel.channelPoint).map {
            val publicKey = Identifier.LndPublicKey
              .untrusted(channel.remotePubkey)
              .getOrElse(
                throw new RuntimeException(s"The $currency lnd returned an invalid public key: ${channel.remotePubkey}")
              )
            OpenChannel(_, channel.active, publicKey)
          }
        }.toList
      }
  }

  override def addInvoice(currency: Currency, amount: Satoshis, memo: String): Future[String] = {
    val invoice = lnrpc.rpc
      .Invoice()
      .withMemo(memo)
      .withValue(amount.valueFor(currency).longValue)

    clientBuilder
      .getLnd(currency)
      .addInvoice(invoice)
      .map(_.paymentRequest)
  }

  override def getTransactions(currency: Currency): Future[List[Transaction]] = {
    val lnd = clientBuilder.getLnd(currency)
    lnd.getTransactions(lnrpc.rpc.GetTransactionsRequest()).map(_.transactions.toList)
  }

  override def subscribeChannelEvents(
      currency: Currency,
      channelEventObserver: StreamObserver[ChannelEventUpdate]
  ): Future[Unit] = {
    Future {
      val lnd = clientBuilder.getLnd(currency)
      lnd.subscribeChannelEvents(lnrpc.rpc.ChannelEventSubscription(), channelEventObserver)
    }
  }

  override def openChannel(
      currency: Currency,
      nodePublicKey: Identifier.LndPublicKey,
      capacity: Satoshis,
      openChannelObserver: StreamObserver[OpenStatusUpdate],
      estimatedSatPerByte: Satoshis
  ): Future[Unit] = {
    val minCapacity = clientBuilder.getMinSize(currency)
    if (capacity.valueFor(currency).longValue < minCapacity) {
      Future.failed {
        new Exception(s"The minimum capacity for a ${currency.entryName} channel is ${minCapacity} satoshis")
      }
    } else {
      Future {
        val nodePubKey = ByteString.copyFrom(nodePublicKey.value.toArray)
        // we take the minimum value between the received from explorer and the configuration, also the fee must be greater than 0
        val satPerByte = clientBuilder
          .getMaxSatPerByte(currency)
          .min(estimatedSatPerByte.valueFor(currency).toLong)
          .max(1)
        val request = lnrpc.rpc
          .OpenChannelRequest()
          .withNodePubkey(nodePubKey)
          .withLocalFundingAmount(capacity.valueFor(currency).longValue)
          .withSatPerByte(satPerByte)

        clientBuilder
          .getLnd(currency)
          .openChannel(request, openChannelObserver)
      }
    }
  }

  override def getClientsHubBalance(currency: Currency): Future[HubLocalBalances] = {
    clientBuilder
      .getLnd(currency)
      .listChannels(ListChannelsRequest())
      .map { response =>
        response.channels.foldLeft(HubLocalBalances.empty(currency)) { (result, channel) =>
          val localBalance = currency
            .satoshis(BigInt(channel.localBalance))
            .getOrElse(throw new RuntimeException(s"LND Returned invalid satoshis: ${channel.localBalance}"))

          result.add(channel.remotePubkey, localBalance)
        }
      }
  }
}
