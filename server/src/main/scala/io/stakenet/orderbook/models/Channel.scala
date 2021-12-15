package io.stakenet.orderbook.models

import java.time.{Duration, Instant}

import io.stakenet.orderbook.models.ChannelIdentifier.{ConnextChannelAddress, LndOutpoint}
import io.stakenet.orderbook.models.clients.{ClientPublicIdentifierId, ClientPublicKeyId, Identifier}
import io.stakenet.orderbook.models.lnd._

sealed trait Channel {
  def channelId: ChannelId
  def createdAt: Option[Instant]
  def expiresAt: Option[Instant]
  def isActive: Boolean

  def isExpired: Boolean = expiresAt.exists(_.isBefore(Instant.now()))
  def remainingTime: Option[Duration] = expiresAt.map(Duration.between(Instant.now, _))
}

object Channel {

  case class LndChannel(
      override val channelId: ChannelId.LndChannelId,
      paymentRHash: PaymentRHash,
      payingCurrency: Currency,
      publicKey: Identifier.LndPublicKey,
      clientPublicKeyId: ClientPublicKeyId,
      fundingTransaction: Option[LndTxid],
      outputIndex: Option[Int],
      override val createdAt: Option[Instant],
      override val expiresAt: Option[Instant],
      status: ChannelStatus,
      closingType: Option[String],
      closedBy: Option[String],
      closedOn: Option[Instant]
  ) extends Channel {

    def withChannelPoint(outPoint: LndOutpoint): LndChannel =
      copy(fundingTransaction = Some(outPoint.txid), outputIndex = Some(outPoint.index))

    def isActive: Boolean = status == ChannelStatus.Active
  }

  object LndChannel {

    def from(
        paymentRHash: PaymentRHash,
        payingCurrency: Currency,
        publicKey: Identifier.LndPublicKey,
        clientPublicKeyId: ClientPublicKeyId,
        status: ChannelStatus
    ): LndChannel = {
      val channelId = ChannelId.LndChannelId.random()
      LndChannel(
        channelId,
        paymentRHash,
        payingCurrency,
        publicKey,
        clientPublicKeyId,
        None,
        None,
        None,
        None,
        status,
        None,
        None,
        None
      )
    }
  }

  case class ConnextChannel(
      override val channelId: ChannelId.ConnextChannelId,
      paymentRHash: PaymentRHash,
      payingCurrency: Currency,
      publicIdentifier: Identifier.ConnextPublicIdentifier,
      clientPublicIdentifierId: ClientPublicIdentifierId,
      channelAddress: Option[ConnextChannelAddress],
      status: ConnextChannelStatus,
      createdAt: Option[Instant],
      expiresAt: Option[Instant]
  ) extends Channel {

    def withChannelAddress(channelAddress: ConnextChannelAddress): ConnextChannel = {
      copy(channelAddress = Some(channelAddress))
    }

    def isActive: Boolean = status == ConnextChannelStatus.Active
  }

  object ConnextChannel {

    def from(
        paymentRHash: PaymentRHash,
        payingCurrency: Currency,
        publicIdentifier: Identifier.ConnextPublicIdentifier,
        clientPublicIdentifierId: ClientPublicIdentifierId,
        status: ConnextChannelStatus,
        lifetimeSeconds: Long
    ): ConnextChannel = {
      val channelId = ChannelId.ConnextChannelId.random()
      val createdAt = Instant.now
      val expiresAt = createdAt.plusSeconds(lifetimeSeconds)

      ConnextChannel(
        channelId,
        paymentRHash,
        payingCurrency,
        publicIdentifier,
        clientPublicIdentifierId,
        channelAddress = None,
        status,
        createdAt = Some(createdAt),
        expiresAt = Some(expiresAt)
      )
    }
  }

}
