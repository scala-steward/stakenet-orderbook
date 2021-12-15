package io.stakenet.orderbook.repositories.channels

import java.time.Instant

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.ChannelIdentifier.{ConnextChannelAddress, LndOutpoint}
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.connext.{ConnextChannel, ConnextChannelContractDeploymentFee}
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Channel, ChannelExtension, ChannelId, Currency, Satoshis}
import javax.inject.Inject

import scala.concurrent.Future

trait ChannelsRepository[F[_]] {
  def createChannelFeePayment(channelFeePayment: ChannelFeePayment, paymentRHash: PaymentRHash, fee: Satoshis): F[Unit]
  def findChannelFeePayment(paymentRHash: PaymentRHash, currency: Currency): F[Option[ChannelFeePayment]]
  def findChannelFeePayment(channelId: ChannelId.LndChannelId): F[Option[ChannelFeePayment]]
  def findChannelFeePayment(channelId: ChannelId.ConnextChannelId): F[Option[ChannelFeePayment]]
  def findChannelFeePayment(outpoint: LndOutpoint): F[Option[ChannelFeePayment]]
  def createChannel(channel: Channel.LndChannel): F[Unit]
  def createChannel(channel: Channel.ConnextChannel, transactionHash: String): F[Unit]
  def findChannel(channelId: ChannelId.LndChannelId): F[Option[Channel.LndChannel]]
  def updateChannelStatus(channelId: ChannelId.LndChannelId, channelStatus: ChannelStatus): F[Unit]
  def updateChannelPoint(channelId: ChannelId.LndChannelId, outPoint: LndOutpoint): F[Unit]
  def updateActiveChannel(channelId: ChannelId.LndChannelId, createdAt: Instant, expiresAt: Instant): F[Unit]
  def updateActiveChannel(outpoint: LndOutpoint, createdAt: Instant, expiresAt: Instant): F[Unit]
  def getExpiredChannels(currency: Currency): F[List[LndChannel]]
  def getConnextExpiredChannels(): F[List[Channel.ConnextChannel]]
  def getProcessingChannels(currency: Currency): F[List[LndChannel]]

  def findChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency
  ): F[Option[ChannelExtension[ChannelId.LndChannelId]]]

  def findConnextChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency
  ): F[Option[ChannelExtension[ChannelId.ConnextChannelId]]]

  def requestRentedChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency,
      channelId: ChannelId.LndChannelId,
      fee: Satoshis,
      seconds: Long
  ): F[Unit]

  def requestRentedChannelExtension(
      paymentHash: PaymentRHash,
      payingCurrency: Currency,
      channelId: ChannelId.ConnextChannelId,
      fee: Satoshis,
      seconds: Long
  ): F[Unit]

  def payRentedChannelExtensionFee(extension: ChannelExtension[ChannelId.LndChannelId]): F[Either[String, Unit]]

  def payConnextRentedChannelExtensionFee(
      extension: ChannelExtension[ChannelId.ConnextChannelId]
  ): F[Either[String, Unit]]

  def findChannel(outPoint: LndOutpoint): F[Option[Channel.LndChannel]]
  def findChannel(paymentHash: PaymentRHash, currency: Currency): F[Option[Channel.LndChannel]]
  def findConnextChannel(paymentHash: PaymentRHash, currency: Currency): F[Option[Channel.ConnextChannel]]
  def findConnextChannel(id: ChannelId.ConnextChannelId): F[Option[Channel.ConnextChannel]]
  def updateClosedChannel(outPoint: LndOutpoint, closingType: String, closedBy: String): F[Unit]
  def createCloseExpiredChannelRequest(channelId: ChannelId, active: Boolean, requestedOn: Instant): F[Unit]
  def setActive(channelAddress: ConnextChannelAddress): F[Unit]
  def setClosed(channelAddress: ConnextChannelAddress): F[Unit]
  def findConnextConfirmingChannels(): F[List[ConnextChannel]]
  def createConnextChannelContractDeploymentFee(transactionHash: String, clientId: ClientId, amount: Satoshis): F[Unit]
  def findConnextChannelContractDeploymentFee(clientId: ClientId): F[Option[ConnextChannelContractDeploymentFee]]
}

object ChannelsRepository {

  type Id[T] = T
  trait Blocking extends ChannelsRepository[Id]

  class FutureImpl @Inject()(blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends ChannelsRepository[scala.concurrent.Future] {

    override def createChannelFeePayment(
        channelFeePayment: ChannelFeePayment,
        paymentRHash: PaymentRHash,
        fee: Satoshis
    ): Future[Unit] = Future {
      blocking.createChannelFeePayment(channelFeePayment, paymentRHash, fee)
    }

    override def findChannelFeePayment(
        paymentRHash: PaymentRHash,
        currency: Currency
    ): Future[Option[ChannelFeePayment]] = Future {
      blocking.findChannelFeePayment(paymentRHash, currency)
    }

    override def findChannelFeePayment(channelId: ChannelId.LndChannelId): Future[Option[ChannelFeePayment]] = Future {
      blocking.findChannelFeePayment(channelId)
    }

    override def findChannelFeePayment(channelId: ChannelId.ConnextChannelId): Future[Option[ChannelFeePayment]] = {
      Future {
        blocking.findChannelFeePayment(channelId)
      }
    }

    override def createChannel(channel: Channel.LndChannel): Future[Unit] = Future {
      blocking.createChannel(channel)
    }

    override def createChannel(channel: Channel.ConnextChannel, transactionHash: String): Future[Unit] = Future {
      blocking.createChannel(channel, transactionHash)
    }

    override def findChannel(channelId: ChannelId.LndChannelId): Future[Option[Channel.LndChannel]] = Future {
      blocking.findChannel(channelId)
    }

    override def updateChannelStatus(channelId: ChannelId.LndChannelId, channelStatus: ChannelStatus): Future[Unit] =
      Future {
        blocking.updateChannelStatus(channelId, channelStatus)
      }

    override def updateChannelPoint(channelId: ChannelId.LndChannelId, outPoint: LndOutpoint): Future[Unit] =
      Future {
        blocking.updateChannelPoint(channelId, outPoint)
      }

    override def updateActiveChannel(
        channelId: ChannelId.LndChannelId,
        createdAt: Instant,
        expiresAt: Instant
    ): Future[Unit] =
      Future {
        blocking.updateActiveChannel(channelId, createdAt, expiresAt)
      }

    override def updateActiveChannel(outpoint: LndOutpoint, createdAt: Instant, expiresAt: Instant): Future[Unit] =
      Future {
        blocking.updateActiveChannel(outpoint, createdAt, expiresAt)
      }

    override def getExpiredChannels(currency: Currency): Future[List[LndChannel]] = Future {
      blocking.getExpiredChannels(currency)
    }

    override def getProcessingChannels(currency: Currency): Future[List[LndChannel]] = Future {
      blocking.getExpiredChannels(currency)
    }

    override def requestRentedChannelExtension(
        paymentHash: PaymentRHash,
        payingCurrency: Currency,
        channelId: ChannelId.LndChannelId,
        fee: Satoshis,
        seconds: Long
    ): Future[Unit] = Future {
      blocking.requestRentedChannelExtension(paymentHash, payingCurrency, channelId, fee, seconds)
    }

    override def requestRentedChannelExtension(
        paymentHash: PaymentRHash,
        payingCurrency: Currency,
        channelId: ChannelId.ConnextChannelId,
        fee: Satoshis,
        seconds: Long
    ): Future[Unit] = Future {
      blocking.requestRentedChannelExtension(paymentHash, payingCurrency, channelId, fee, seconds)
    }

    override def payRentedChannelExtensionFee(
        extension: ChannelExtension[ChannelId.LndChannelId]
    ): Future[Either[String, Unit]] = Future {
      blocking.payRentedChannelExtensionFee(extension)
    }

    override def payConnextRentedChannelExtensionFee(
        extension: ChannelExtension[ChannelId.ConnextChannelId]
    ): Future[Either[String, Unit]] = Future {
      blocking.payConnextRentedChannelExtensionFee(extension)
    }

    override def findChannelExtension(
        paymentHash: PaymentRHash,
        payingCurrency: Currency
    ): Future[Option[ChannelExtension[ChannelId.LndChannelId]]] = Future {
      blocking.findChannelExtension(paymentHash, payingCurrency)
    }

    override def findConnextChannelExtension(
        paymentHash: PaymentRHash,
        payingCurrency: Currency
    ): Future[Option[ChannelExtension[ChannelId.ConnextChannelId]]] = Future {
      blocking.findConnextChannelExtension(paymentHash, payingCurrency)
    }

    override def findChannel(outPoint: LndOutpoint): Future[Option[Channel.LndChannel]] = Future {
      blocking.findChannel(outPoint)
    }

    override def updateClosedChannel(outPoint: LndOutpoint, closingType: String, closedBy: String): Future[Unit] =
      Future {
        blocking.updateClosedChannel(outPoint, closingType, closedBy)
      }

    override def createCloseExpiredChannelRequest(
        channelId: ChannelId,
        active: Boolean,
        requestedOn: Instant
    ): Future[Unit] = Future {
      blocking.createCloseExpiredChannelRequest(channelId, active, requestedOn)
    }

    override def findChannelFeePayment(outpoint: LndOutpoint): Future[Option[ChannelFeePayment]] = Future {
      blocking.findChannelFeePayment(outpoint)
    }

    override def findChannel(paymentHash: PaymentRHash, currency: Currency): Future[Option[Channel.LndChannel]] =
      Future {
        blocking.findChannel(paymentHash, currency)
      }

    override def findConnextChannel(
        paymentHash: PaymentRHash,
        currency: Currency
    ): Future[Option[Channel.ConnextChannel]] = Future {
      blocking.findConnextChannel(paymentHash, currency)
    }

    override def setActive(channelAddress: ConnextChannelAddress): Future[Unit] = Future {
      blocking.setActive(channelAddress)
    }

    override def setClosed(channelAddress: ConnextChannelAddress): Future[Unit] = Future {
      blocking.setClosed(channelAddress)
    }

    override def findConnextConfirmingChannels(): Future[List[ConnextChannel]] = Future {
      blocking.findConnextConfirmingChannels()
    }

    override def findConnextChannel(id: ChannelId.ConnextChannelId): Future[Option[Channel.ConnextChannel]] = Future {
      blocking.findConnextChannel(id)
    }

    override def getConnextExpiredChannels(): Future[List[Channel.ConnextChannel]] = Future {
      blocking.getConnextExpiredChannels()
    }

    override def createConnextChannelContractDeploymentFee(
        transactionHash: String,
        clientId: ClientId,
        amount: Satoshis
    ): Future[Unit] =
      Future {
        blocking.createConnextChannelContractDeploymentFee(transactionHash, clientId, amount)
      }

    override def findConnextChannelContractDeploymentFee(
        clientId: ClientId
    ): Future[Option[ConnextChannelContractDeploymentFee]] = Future {
      blocking.findConnextChannelContractDeploymentFee(clientId)
    }
  }
}
