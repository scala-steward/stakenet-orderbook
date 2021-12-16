package io.stakenet.orderbook.repositories.channels

import java.time.Instant

import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.connext.{ConnextChannel, ConnextChannelContractDeploymentFee}
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.repositories.channels.ChannelsRepository.Id
import javax.inject.Inject
import play.api.db.Database

class ChannelsPostgresRepository @Inject() (database: Database) extends ChannelsRepository.Blocking {

  override def createChannelFeePayment(
      channelFeePayment: ChannelFeePayment,
      paymentRHash: PaymentRHash,
      fee: Satoshis
  ): Unit = {
    database.withConnection { implicit conn =>
      ChannelsDAO.createChannelPayment(channelFeePayment, paymentRHash, fee)
    }
  }

  override def findChannelFeePayment(paymentRHash: PaymentRHash, currency: Currency): Option[ChannelFeePayment] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannelFeePayment(paymentRHash, currency)
    }
  }

  override def findChannelFeePayment(channelId: ChannelId.LndChannelId): Option[ChannelFeePayment] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannelFeePayment(channelId)
    }
  }

  override def findChannelFeePayment(channelId: ChannelId.ConnextChannelId): Option[ChannelFeePayment] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannelFeePayment(channelId)
    }
  }

  override def createChannel(channel: Channel.LndChannel): Unit = {
    database.withConnection { implicit conn =>
      ChannelsDAO.createChannel(channel)
    }
  }

  override def createChannel(channel: Channel.ConnextChannel, transactionHash: String): Unit = {
    database.withConnection { implicit conn =>
      ChannelsDAO.createChannel(channel, transactionHash)
    }
  }

  override def findChannel(channelId: ChannelId.LndChannelId): Option[Channel.LndChannel] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannel(channelId)
    }
  }

  override def updateChannelStatus(channelId: ChannelId.LndChannelId, channelStatus: ChannelStatus): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateChannelStatus(channelId, channelStatus)
    }
  }

  override def updateChannelPoint(channelId: ChannelId.LndChannelId, outPoint: LndOutpoint): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateChannelPoint(channelId, outPoint)
    }
  }

  override def updateActiveChannel(
      channelId: ChannelId.LndChannelId,
      createdAt: Instant,
      expiresAt: Instant
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateActiveChannel(channelId, createdAt, expiresAt)
    }
  }

  override def updateActiveChannel(outpoint: LndOutpoint, createdAt: Instant, expiresAt: Instant): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateActiveChannel(outpoint, createdAt, expiresAt)
    }
  }

  override def getExpiredChannels(currency: Currency): Id[List[LndChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.getExpiredChannels(currency)
    }
  }

  override def getProcessingChannels(currency: Currency): Id[List[LndChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.getProcessingChannels(currency)
    }
  }

  override def requestRentedChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency,
      channelId: ChannelId.LndChannelId,
      fee: Satoshis,
      seconds: Long
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      val channelExtension = ChannelExtension(
        paymentHash,
        payingCurrency,
        channelId,
        fee,
        seconds,
        Instant.now,
        None
      )

      ChannelsDAO.createRentedChannelExtensionRequest(channelExtension)
    }
  }

  override def requestRentedChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency,
      channelId: ChannelId.ConnextChannelId,
      fee: Satoshis,
      seconds: Long
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      val channelExtension = ChannelExtension(
        paymentHash,
        payingCurrency,
        channelId,
        fee,
        seconds,
        Instant.now,
        None
      )

      ChannelsDAO.createConnextChannelExtensionRequest(channelExtension)
    }
  }

  override def payRentedChannelExtensionFee(
      extension: ChannelExtension[ChannelId.LndChannelId]
  ): Id[Either[String, Unit]] = {
    database.withTransaction { implicit conn =>
      val paidAt = Instant.now()
      val paymentRHash = extension.paymentHash
      val payingCurrency = extension.payingCurrency

      ChannelsDAO.createRentedChannelExtensionFeePayment(paymentRHash, payingCurrency, paidAt)

      for {
        channel <- ChannelsDAO
          .findChannelForUpdate(extension.channelId)
          .toRight(s"channel ${extension.channelId} not found")
        currentExpirationDate <- channel.expiresAt
          .toRight(s"Channel ${channel.channelId} has no expiration date")
        newExpirationDate = currentExpirationDate.plusSeconds(extension.seconds)
      } yield ChannelsDAO.updateChannelExpirationDate(channel.channelId, newExpirationDate)
    }
  }

  override def payConnextRentedChannelExtensionFee(
      extension: ChannelExtension[ChannelId.ConnextChannelId]
  ): Id[Either[String, Unit]] = {
    database.withTransaction { implicit conn =>
      val paidAt = Instant.now()
      val paymentRHash = extension.paymentHash
      val payingCurrency = extension.payingCurrency
      ChannelsDAO.createConnextRentedChannelExtensionFeePayment(paymentRHash, payingCurrency, paidAt)

      for {
        channel <- ChannelsDAO
          .findChannelForUpdate(extension.channelId)
          .toRight(s"channel $extension.channelId not found")
        currentExpirationDate <- channel.expiresAt
          .toRight(s"Channel ${channel.channelId} has no expiration date")
        newExpirationDate = currentExpirationDate.plusSeconds(extension.seconds)
      } yield ChannelsDAO.updateChannelExpirationDate(channel.channelId, newExpirationDate)
    }
  }

  override def findChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency
  ): Id[Option[ChannelExtension[ChannelId.LndChannelId]]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannelExtension(paymentHash, payingCurrency)
    }
  }

  override def findChannel(outPoint: LndOutpoint): Id[Option[Channel.LndChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannel(outPoint)
    }
  }

  override def updateClosedChannel(outPoint: LndOutpoint, closingType: String, closedBy: String): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateClosedChannel(outPoint, closingType, closedBy, Instant.now())
    }
  }

  override def createCloseExpiredChannelRequest(
      channelId: ChannelId,
      active: Boolean,
      requestedOn: Instant
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.createCloseExpiredChannelRequest(channelId, active, requestedOn)
    }
  }

  override def findChannelFeePayment(outpoint: LndOutpoint): Id[Option[ChannelFeePayment]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannelFeePayment(outpoint)
    }
  }

  override def findChannel(paymentHash: PaymentRHash, currency: Currency): Id[Option[Channel.LndChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findChannel(paymentHash, currency)
    }
  }

  override def findConnextChannel(paymentHash: PaymentRHash, currency: Currency): Id[Option[Channel.ConnextChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findConnextChannel(paymentHash, currency)
    }
  }

  override def setActive(channelAddress: ChannelIdentifier.ConnextChannelAddress): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateChannelStatus(channelAddress, ConnextChannelStatus.Active)
    }
  }

  override def setClosed(channelAddress: ChannelIdentifier.ConnextChannelAddress): Id[Unit] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.updateChannelStatus(channelAddress, ConnextChannelStatus.Closed)
    }
  }

  override def findConnextConfirmingChannels(): Id[List[ConnextChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findConfirmingConnextChannels()
    }
  }

  override def findConnextChannel(id: ChannelId.ConnextChannelId): Id[Option[Channel.ConnextChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findConnextChannel(id)
    }
  }

  override def getConnextExpiredChannels(): Id[List[Channel.ConnextChannel]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.getConnextExpiredChannels()
    }
  }

  override def findConnextChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency
  ): Id[Option[ChannelExtension[ChannelId.ConnextChannelId]]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findConnextChannelExtension(paymentHash, payingCurrency)
    }
  }

  override def createConnextChannelContractDeploymentFee(
      transactionHash: String,
      clientId: ClientId,
      amount: Satoshis
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      val fee = ConnextChannelContractDeploymentFee(transactionHash, clientId, amount, Instant.now)

      ChannelsDAO.createConnextChannelContractDeploymentFee(fee)
    }
  }

  override def findConnextChannelContractDeploymentFee(
      clientId: ClientId
  ): Id[Option[ConnextChannelContractDeploymentFee]] = {
    database.withConnection { implicit conn =>
      ChannelsDAO.findConnextChannelContractDeploymentFee(clientId)
    }
  }
}
