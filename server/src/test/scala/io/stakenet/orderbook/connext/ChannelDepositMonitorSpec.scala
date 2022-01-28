package io.stakenet.orderbook.connext

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKitBase
import helpers.Helpers
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.helpers.{Executors, SampleChannels}
import io.stakenet.orderbook.models.clients.ClientIdentifier.ClientConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.repositories.channels
import io.stakenet.orderbook.repositories.channels.ChannelsPostgresRepository
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.services.ExplorerService
import org.mockito.MockitoSugar._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.Eventually._
import org.scalatest.matchers.must.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._

class ChannelDepositMonitorSpec extends PostgresRepositorySpec with TestKitBase with Matchers with BeforeAndAfterAll {
  implicit lazy val system: ActorSystem = ActorSystem("ChannelDepositMonitorSpec")
  lazy val channelsRepository = new ChannelsPostgresRepository(database)

  override def afterAll(): Unit = {
    super.afterAll()

    shutdown(system)
  }

  "monitor" should {
    "should update channel balance after 12 confirmations" in {
      val ethService = mock[ExplorerService]
      val connextHelper = mock[ConnextHelper]
      val monitor = getMonitor(ethService, connextHelper)

      val channelAddress = Helpers.randomChannelAddress()
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val currency = Currency.WETH

      val channel = createChannelWithAddress(channelAddress)
      val transaction = ExplorerService.Transaction(BigInt(20), "address", Satoshis.Zero)

      when(ethService.getTransaction(currency, transactionHash)).thenReturn(Future.successful(Right(transaction)))
      when(ethService.getLatestBlockNumber(currency)).thenReturn(Future.successful(Right(35)))
      when(connextHelper.updateChannelBalance(channelAddress, currency)).thenReturn(Future.unit)

      monitor.monitor(channelAddress, transactionHash, currency)

      eventually {
        verify(connextHelper).updateChannelBalance(channelAddress, currency)

        val result = channelsRepository.findConnextChannel(channel.paymentRHash, channel.payingCurrency).value
        result.status mustBe ConnextChannelStatus.Active
      }
    }

    "should keep retrying until transactions has 12 confirmations" in {
      val ethService = mock[ExplorerService]
      val connextHelper = mock[ConnextHelper]
      val monitor = getMonitor(ethService, connextHelper)

      val channelAddress = Helpers.randomChannelAddress()
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val currency = Currency.WETH

      val channel = createChannelWithAddress(channelAddress)
      val transaction = ExplorerService.Transaction(BigInt(20), "address", Satoshis.Zero)

      when(ethService.getTransaction(currency, transactionHash)).thenReturn(Future.successful(Right(transaction)))
      when(ethService.getLatestBlockNumber(currency)).thenReturn(
        Future.successful(Right(20)),
        Future.successful(Right(25)),
        Future.successful(Right(35))
      )
      when(connextHelper.updateChannelBalance(channelAddress, currency)).thenReturn(Future.unit)

      monitor.monitor(channelAddress, transactionHash, currency)

      implicit val eventuallyPatienceConfig: Eventually.PatienceConfig = Eventually.PatienceConfig(
        30.seconds,
        300.millis
      )

      eventually {
        verify(ethService, times(3)).getLatestBlockNumber(currency)
        verify(connextHelper).updateChannelBalance(channelAddress, currency)

        val result = channelsRepository.findConnextChannel(channel.paymentRHash, channel.payingCurrency).value
        result.status mustBe ConnextChannelStatus.Active
      }
    }
  }

  private def getMonitor(ethService: ExplorerService, connextHelper: ConnextHelper): ChannelDepositMonitor = {
    implicit val ec: DatabaseExecutionContext = Executors.databaseEC
    implicit val scheduler: Scheduler = system.scheduler

    val channelsRepositoryAsync = new channels.ChannelsRepository.FutureImpl(channelsRepository)
    val retryConfig = RetryConfig(10.milliseconds, 30.seconds)

    new ChannelDepositMonitor(ethService, connextHelper, channelsRepositoryAsync, retryConfig)
  }

  private def createChannelWithAddress(address: ChannelIdentifier.ConnextChannelAddress): Channel.ConnextChannel = {
    val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
    val channelFeePayment = SampleChannels.newChannelFeePayment()
    val publicIdentifier = Helpers.randomPublicIdentifier()
    val clientPublicIdentifier = createClientPublicIdentifier(publicIdentifier, Currency.XSN)
    val channel = SampleChannels
      .newConnextChannel()
      .copy(
        status = ConnextChannelStatus.Confirming,
        channelAddress = Some(address),
        payingCurrency = channelFeePayment.payingCurrency,
        publicIdentifier = publicIdentifier,
        clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
      )

    channelsRepository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
    channelsRepository.createChannel(channel, transactionHash)

    channel
  }

  private def createClientPublicIdentifier(
      identifier: ConnextPublicIdentifier,
      currency: Currency
  ): ClientConnextPublicIdentifier = {
    val clientsRepository = new ClientsPostgresRepository(database)
    val walletId = Helpers.randomWalletId()

    val clientId = clientsRepository.createWalletClient(walletId)
    clientsRepository.registerPublicIdentifier(clientId, identifier, currency)

    clientsRepository.findPublicIdentifier(clientId, currency).value
  }
}
