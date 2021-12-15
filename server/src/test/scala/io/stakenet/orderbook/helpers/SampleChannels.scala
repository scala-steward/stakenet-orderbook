package io.stakenet.orderbook.helpers

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.lnd._

object SampleChannels {

  def newChannelFeePayment(): ChannelFeePayment = {
    val currency = Currency.BTC
    val payingCurrency = Currency.XSN
    val capacity = Helpers.asSatoshis("5.0")
    val lifeTimeSeconds = 64000000L
    ChannelFeePayment(currency, payingCurrency, capacity, lifeTimeSeconds, Satoshis.Zero)
  }

  def newChannel(): Channel.LndChannel = {
    val channelId = ChannelId.LndChannelId.random()
    val paymentRHash = Helpers.randomPaymentHash()
    val publicKey = Helpers.randomPublicKey()
    val fundingTransaction = None
    val outputIndex = None;
    val createdAt = None
    val expiresAt = None
    val channelStatus = ChannelStatus.Opening
    val clientPublicKey = Helpers.randomClientPublicKey()

    Channel.LndChannel(
      channelId,
      paymentRHash,
      Currency.XSN,
      publicKey,
      clientPublicKey.clientPublicKeyId,
      fundingTransaction,
      outputIndex,
      createdAt,
      expiresAt,
      channelStatus,
      None,
      None,
      None
    )
  }

  def newConnextChannel(): Channel.ConnextChannel = {
    val channelId = ChannelId.ConnextChannelId.random()
    val paymentRHash = Helpers.randomPaymentHash()
    val publicIdentifier = Helpers.randomClientPublicIdentifier()
    val channelAddress = Some(Helpers.randomChannelAddress())
    val createdAt = Some(Instant.now)
    val expiresAt = Some(Instant.now)
    val channelStatus = ConnextChannelStatus.Active

    Channel.ConnextChannel(
      channelId,
      paymentRHash,
      Currency.XSN,
      publicIdentifier.identifier,
      publicIdentifier.clientPublicIdentifierId,
      channelAddress,
      channelStatus,
      createdAt,
      expiresAt
    )
  }

  def newChannelExtension(): ChannelExtension[ChannelId.LndChannelId] = {
    val channelId = ChannelId.LndChannelId.random()
    val paymentRHash = Helpers.randomPaymentHash()
    val currency = Currency.XSN
    val fee = Satoshis.One
    val seconds = 100L
    val requestedAt = Instant.now()
    val paidAt = None

    ChannelExtension(paymentRHash, currency, channelId, fee, seconds, requestedAt, paidAt)
  }

  val lndChannelPayment1 = {
    ChannelFeePayment(
      currency = Currency.BTC,
      payingCurrency = Currency.XSN,
      capacity = Helpers.asSatoshis("0.0005"),
      lifeTimeSeconds = 80000,
      Satoshis.Zero
    )
  }

  val XsnBtcPrice = Helpers.asSatoshis("0.0000065")
  val LtcBtcPrice = Helpers.asSatoshis("0.00597465")

}
