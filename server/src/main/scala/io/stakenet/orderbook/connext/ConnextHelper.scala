package io.stakenet.orderbook.connext

import java.time.Instant

import io.stakenet.orderbook.connext.ConnextHelper.ResolveTransferError
import io.stakenet.orderbook.connext.ConnextHelper.ResolveTransferError.{
  CouldNotResolveTransfer,
  NoChannelWithCounterParty,
  TransferNotFound
}
import io.stakenet.orderbook.models.ChannelIdentifier.ConnextChannelAddress
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.connext.{Channel, TransferData}
import io.stakenet.orderbook.models.lnd.{PaymentData, PaymentRHash}
import io.stakenet.orderbook.models.{ChannelIdentifier, Currency, Preimage, Satoshis}
import io.stakenet.orderbook.utils.Extensions._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class ConnextHelper @Inject() (configBuilder: ConnextConfigBuilder, ws: WSClient)(implicit
    ec: ExecutionContext
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def getChannel(channelAddress: ConnextChannelAddress, currency: Currency): Future[Channel] = {
    val publicIdentifier = getPublicIdentifier(currency)
    val url = buildUrl(s"$publicIdentifier/channels/$channelAddress", currency)

    ws.url(url).get().map(_.json).map { response =>
      val channelAddress = ChannelIdentifier
        .ConnextChannelAddress((response \ "channelAddress").as[String])
        .getOrElse(throw new RuntimeException("invalid channel address"))

      val counterPartyIdentifier = (response \ "aliceIdentifier").as[String]

      Channel(channelAddress, Identifier.ConnextPublicIdentifier(counterPartyIdentifier))
    }
  }

  def getChannelLocalBalance(channelAddress: ConnextChannelAddress, currency: Currency): Future[Satoshis] = {
    val publicIdentifier = getPublicIdentifier(currency)
    val signerAddress = getSignerAddress(currency)
    val assetId = getAssetId(currency)
    val url = buildUrl(s"$publicIdentifier/channels/$channelAddress", currency)

    ws.url(url).get().map(_.json).map { response =>
      val assets = (response \ "assetIds").as[List[String]]
      val balances = (response \ "balances").as[List[JsValue]]

      if (assets.contains(assetId)) {
        val currencyBalances = balances(assets.indexOf(assetId))
        val to = (currencyBalances \ "to").as[List[String]]
        val amounts = (currencyBalances \ "amount").as[List[String]]
        val amount = amounts(to.indexOf(signerAddress))

        currency.satoshis(amount).getOrElse(throw new RuntimeException("invalid satoshis"))
      } else {
        Satoshis.Zero
      }
    }
  }

  def getAllChannels(currency: Currency): Future[List[Channel]] = {
    val publicIdentifier = getPublicIdentifier(currency)
    val url = buildUrl(s"$publicIdentifier/channels", currency)

    ws.url(url).get().map(_.json).flatMap { response =>
      Future.sequence(
        response.asOpt[List[String]].getOrElse(List.empty).map { address =>
          val channelAddress = ConnextChannelAddress(address)
            .getOrElse(throw new RuntimeException("invalid channel address"))

          getChannel(channelAddress, currency)
        }
      )
    }
  }

  def openChannel(
      counterPartyPublicIdentifier: ConnextPublicIdentifier,
      currency: Currency
  ): Future[ConnextChannelAddress] = {
    val chainId = getChainId(currency)
    val publicIdentifier = getPublicIdentifier(currency)
    val url = buildUrl("setup", currency)

    val body = Json.parse(
      s"""{
         |  "counterpartyIdentifier": "$counterPartyPublicIdentifier",
         |  "publicIdentifier": "$publicIdentifier",
         |  "chainId": "$chainId",
         |  "timeout": "86400"
         |}""".stripMargin
    )

    ws.url(url).post(body).map(_.json).map { response =>
      val address = (response \ "channelAddress").as[String]

      ConnextChannelAddress(address).getOrElse(throw new RuntimeException("invalid channel address"))
    }
  }

  def getCounterPartyChannelAddress(
      currency: Currency,
      counterPartyPublicIdentifier: Identifier.ConnextPublicIdentifier
  ): Future[Option[ConnextChannelAddress]] = {
    val publicIdentifier = getPublicIdentifier(currency)
    val chainId = getChainId(currency)

    val url = buildUrl(
      s"$publicIdentifier/channels/counterparty/$counterPartyPublicIdentifier/chain-id/$chainId",
      currency
    )

    ws.url(url).get().map(_.json).map { response =>
      (response \ "channelAddress").asOpt[String].map { channelAddress =>
        ConnextChannelAddress(channelAddress)
          .getOrElse(throw new RuntimeException(s"invalid channel address $channelAddress"))
      }
    }
  }

  private def getTransferData(
      currency: Currency,
      channelAddress: ConnextChannelAddress,
      paymentHash: PaymentRHash
  ): Future[Option[TransferData]] = {
    val publicIdentifier = getPublicIdentifier(currency)
    val url = buildUrl(s"$publicIdentifier/channels/$channelAddress/active-transfers", currency)

    ws.url(url).get().map(_.json).map { response =>
      response
        .asOpt[List[JsValue]]
        .getOrElse(List.empty)
        .find { transfer =>
          (transfer \ "transferState" \ "lockHash").as[String].toLowerCase == s"0x${paymentHash.toString}".toLowerCase
        }
        .map { transfer =>
          val id = (transfer \ "transferId").as[String]
          val initiator = (transfer \ "initiator").as[String]
          val to = (transfer \ "balance" \ "to").as[List[String]]
          val amounts = (transfer \ "balance" \ "amount").as[List[String]]
          val amount = amounts(to.indexOf(initiator))
          val fee = currency
            .satoshis(amount)
            .getOrElse(throw new RuntimeException(s"Could not parse $amount as Satoshis"))

          TransferData(id, fee)
        }
    }
  }

  def resolveTransfer(
      currency: Currency,
      counterPartyPublicIdentifier: Identifier.ConnextPublicIdentifier,
      paymentHash: PaymentRHash,
      preimage: Preimage
  ): Future[Either[ResolveTransferError, PaymentData]] = {
    val url = buildUrl("transfers/resolve", currency)
    val publicIdentifier = getPublicIdentifier(currency)

    val result = for {
      channelAddress <- getCounterPartyChannelAddress(currency, counterPartyPublicIdentifier)
        .map(_.toRight[ResolveTransferError](NoChannelWithCounterParty(counterPartyPublicIdentifier)))
        .toFutureEither()

      tranferData <- getTransferData(currency, channelAddress, paymentHash)
        .map(_.toRight[ResolveTransferError](TransferNotFound(channelAddress, paymentHash)))
        .toFutureEither()

      body = Json.parse(
        s"""{
           |  "publicIdentifier": "$publicIdentifier",
           |  "channelAddress": "$channelAddress",
           |  "transferId": "${tranferData.transferId}",
           |  "transferResolver": {
           |    "preImage": "0x$preimage"
           |  }
           |}""".stripMargin
      )
      response <- ws
        .url(url)
        .post(body)
        .map { response =>
          if (response.status == 200) {
            Right[ResolveTransferError, PaymentData](PaymentData(tranferData.amount, Instant.now))
          } else {
            logger.info(s"Error resolving transfer. status = ${response.status}; response = ${response.body}")

            Left[ResolveTransferError, PaymentData](CouldNotResolveTransfer(response.status, response.body))
          }
        }
        .toFutureEither()
    } yield response

    result.toFuture
  }

  def channelDeposit(
      channelAddress: ConnextChannelAddress,
      amount: Satoshis,
      currency: Currency
  ): Future[String] = {
    val url = buildUrl("send-deposit-tx", currency)
    val assetId = getAssetId(currency)
    val chainId = getChainId(currency)
    val publicIdentifier = getPublicIdentifier(currency)

    val body = Json.parse(
      s"""{
         |  "channelAddress": "$channelAddress",
         |  "amount": "${amount.valueFor(currency)}",
         |  "assetId": "$assetId",
         |  "chainId": "$chainId",
         |  "publicIdentifier": "$publicIdentifier"
         |}""".stripMargin
    )

    ws.url(url)
      .post(body)
      .map { response =>
        logger.info(s"$url result. status = ${response.status}; response = ${response.body}")

        (response.json \ "txHash").as[String]
      }
  }

  def channelWithdrawal(
      channelAddress: ConnextChannelAddress,
      amount: Satoshis,
      currency: Currency
  ): Future[String] = {
    val url = buildUrl("withdraw", currency)
    val assetId = getAssetId(currency)
    val publicIdentifier = getPublicIdentifier(currency)
    val recipient = getWithdrawAddress(currency)

    val body = Json.parse(
      s"""{
         |  "channelAddress": "$channelAddress",
         |  "amount": $amount,
         |  "assetId": "$assetId",
         |  "recipient": "$recipient",
         |  "publicIdentifier": "$publicIdentifier"
         |}""".stripMargin
    )

    ws.url(url)
      .post(body)
      .map { response =>
        logger.info(s"$url result. status = ${response.status}; response = ${response.body}")

        (response.json \ "transactionHash").as[String]
      }
  }

  def updateChannelBalance(channelAddress: ConnextChannelAddress, currency: Currency): Future[Unit] = {
    val url = buildUrl("deposit", currency)
    val assetId = getAssetId(currency)
    val publicIdentifier = getPublicIdentifier(currency)

    val body = Json.parse(
      s"""{
         |  "channelAddress": "$channelAddress",
         |  "assetId": "$assetId",
         |  "publicIdentifier": "$publicIdentifier"
         |}""".stripMargin
    )

    ws.url(url)
      .post(body)
      .map { response =>
        logger.info(s"$url result. status = ${response.status}; response = ${response.body}")

        if (response.status != 200) {
          throw new RuntimeException("Error updating the channel balance")
        }

        ()
      }
  }

  private def getHost(currency: Currency): String = {
    configBuilder.getConfig(currency).host
  }

  private def getPort(currency: Currency): String = {
    configBuilder.getConfig(currency).port
  }

  private def buildUrl(url: String, currency: Currency): String = {
    val host = getHost(currency)
    val port = getPort(currency)

    s"$host:$port/$url"
  }

  def getPublicIdentifier(currency: Currency): String = {
    configBuilder.getConfig(currency).publicIdentifier
  }

  private def getChainId(currency: Currency): String = {
    configBuilder.getConfig(currency).chainId
  }

  private def getAssetId(currency: Currency): String = {
    configBuilder.getConfig(currency).assetId
  }

  private def getSignerAddress(currency: Currency): String = {
    configBuilder.getConfig(currency).signerAddress
  }

  private def getWithdrawAddress(currency: Currency): String = {
    configBuilder.getConfig(currency).withdrawAddress
  }
}

object ConnextHelper {
  trait ResolveTransferError

  object ResolveTransferError {

    final case class NoChannelWithCounterParty(
        counterPartyPublicIdentifier: Identifier.ConnextPublicIdentifier
    ) extends ResolveTransferError

    final case class TransferNotFound(
        channelAddress: ConnextChannelAddress,
        paymentHash: PaymentRHash
    ) extends ResolveTransferError
    final case class CouldNotResolveTransfer(status: Int, Body: String) extends ResolveTransferError
  }
}
