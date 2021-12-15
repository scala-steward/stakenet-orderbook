package io.stakenet.orderbook.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import io.stakenet.orderbook.actors.connection.ConnectionManagerActor
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers.{PeerActor, PeerUser}
import io.stakenet.orderbook.config._
import io.stakenet.orderbook.connext.{ChannelDepositMonitor, ConnextHelper}
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.explorer.EstimatedFee
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.repositories.preimages.PreimagesRepository
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.repositories.{channels, reports}
import io.stakenet.orderbook.services.ChannelService.ChannelImp
import io.stakenet.orderbook.services.ClientService.ClientServiceImpl
import io.stakenet.orderbook.services.apis.PriceApi
import io.stakenet.orderbook.services.impl.{LndFeeService, SimpleOrderMatcherService}
import io.stakenet.orderbook.services.validators.OrderValidator
import io.stakenet.orderbook.services.{
  CurrencyConverter,
  ETHService,
  ExplorerService,
  MakerPaymentService,
  PaymentService,
  UsdConverter
}
import org.mockito.MockitoSugar.mock
import org.scalatest.OptionValues._

import scala.concurrent.Future
import scala.concurrent.duration._

class PeerSpecBase(actorSystemName: String) extends TestKit(ActorSystem(actorSystemName)) with PostgresRepositorySpec {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  protected def discardMsg(peer: Peer): Unit = {
    peer.client.expectMsgPF() {
      case msg => println(s"Discarding message: $msg")
    }
  }

  protected def nextMsg(peer: Peer): Any = {
    peer.client.expectMsgPF() {
      case msg => msg
    }
  }

  protected def withPeers[T](
      feesEnabled: Boolean = false,
      feesRepository: FeesRepository.Blocking = mock[FeesRepository.Blocking],
      paymentService: PaymentService = mock[PaymentService],
      orderFeesConfig: OrderFeesConfig = defaultOrderFeesConfig,
      feeRefundsRepository: FeeRefundsRepository.Blocking = mock[FeeRefundsRepository.Blocking],
      lnd: MulticurrencyLndClient = mock[MulticurrencyLndClient],
      channelsRepository: ChannelsRepository.Blocking = mock[ChannelsRepository.Blocking],
      tradesConfig: TradesConfig = defaultTradesConfig,
      tradingPairsConfig: TradingPairsConfig = defaultTradingPairsConfig,
      tradesRepository: TradesRepository.Blocking = mock[TradesRepository.Blocking],
      reportsRepository: ReportsRepository.Blocking = mock[ReportsRepository.Blocking],
      clientsRepository: ClientsRepository.Blocking = mock[ClientsRepository.Blocking],
      connextHelper: ConnextHelper = mock[ConnextHelper],
      makerPaymentService: MakerPaymentService = mock[MakerPaymentService],
      preimagesRepository: PreimagesRepository.Blocking = mock[PreimagesRepository.Blocking],
      ethService: ETHService = mock[ETHService]
  )(
      names: String*
  )(f: PartialFunction[TestData, T]): T = {

    val orderMatcher = new SimpleOrderMatcherService()
    val orderManagerName = s"order-manager${UUID.randomUUID()}"
    val orderManager = OrderManagerActor.Ref(orderMatcher, tradesConfig, orderManagerName)

    val peerMessageFilterName = s"peer-message-filter${UUID.randomUUID()}"
    val peerMessageFilter = PeerMessageFilterActor.Ref(peerMessageFilterName)

    val connectionManagerName = s"connection-manager${UUID.randomUUID()}"
    val connectionManager = ConnectionManagerActor.Ref(connectionManagerName)

    val peers = names.map { name =>
      newPeer(
        orderManager,
        peerMessageFilter,
        connectionManager,
        name + UUID.randomUUID().toString,
        paymentService,
        feesEnabled,
        feesRepository,
        orderFeesConfig = orderFeesConfig,
        feeRefundsRepository,
        lnd,
        channelsRepository,
        tradingPairsConfig,
        tradesRepository,
        reportsRepository,
        clientsRepository,
        connextHelper,
        makerPaymentService,
        preimagesRepository,
        ethService
      )
    }
    val data = TestData(peers.toList, orderManager, peerMessageFilter)
    try {
      f(data)
    } finally {
      orderManager.ref ! PoisonPill
      peers.foreach(_.actor ! PoisonPill)
    }
  }

  protected def withSinglePeer[T](
      feesEnabled: Boolean = false,
      feesRepository: FeesRepository.Blocking = mock[FeesRepository.Blocking],
      paymentService: PaymentService = mock[PaymentService],
      orderFeesConfig: OrderFeesConfig = defaultOrderFeesConfig,
      feeRefundsRepository: FeeRefundsRepository.Blocking = mock[FeeRefundsRepository.Blocking],
      lnd: MulticurrencyLndClient = mock[MulticurrencyLndClient],
      channelsRepository: ChannelsRepository.Blocking = mock[ChannelsRepository.Blocking],
      tradesConfig: TradesConfig = defaultTradesConfig,
      tradingPairsConfig: TradingPairsConfig = defaultTradingPairsConfig,
      tradesRepository: TradesRepository.Blocking = mock[TradesRepository.Blocking],
      reportsRepository: ReportsRepository.Blocking = mock[ReportsRepository.Blocking],
      clientsRepository: ClientsRepository.Blocking = mock[ClientsRepository.Blocking],
      connextHelper: ConnextHelper = mock[ConnextHelper],
      makerPaymentService: MakerPaymentService = mock[MakerPaymentService],
      preimagesRepository: PreimagesRepository.Blocking = mock[PreimagesRepository.Blocking],
      ethService: ETHService = mock[ETHService]
  )(
      f: Peer => T
  ): T = {
    withPeers(
      feesEnabled,
      feesRepository,
      paymentService,
      orderFeesConfig,
      feeRefundsRepository,
      lnd,
      channelsRepository,
      tradesConfig,
      tradingPairsConfig,
      tradesRepository,
      reportsRepository,
      clientsRepository,
      connextHelper,
      makerPaymentService,
      preimagesRepository,
      ethService
    )("alice") {
      case TestData(alice :: Nil, _, _) => f(alice)
    }
  }

  protected def withTwoPeers[T](
      feesEnabled: Boolean = false,
      feesRepository: FeesRepository.Blocking = mock[FeesRepository.Blocking],
      paymentService: PaymentService = mock[PaymentService],
      orderFeesConfig: OrderFeesConfig = defaultOrderFeesConfig,
      feeRefundsRepository: FeeRefundsRepository.Blocking = mock[FeeRefundsRepository.Blocking],
      lnd: MulticurrencyLndClient = mock[MulticurrencyLndClient],
      channelsRepository: ChannelsRepository.Blocking = mock[ChannelsRepository.Blocking],
      tradesConfig: TradesConfig = defaultTradesConfig,
      tradingPairsConfig: TradingPairsConfig = defaultTradingPairsConfig,
      tradesRepository: TradesRepository.Blocking = mock[TradesRepository.Blocking],
      reportsRepository: ReportsRepository.Blocking = mock[ReportsRepository.Blocking],
      clientsRepository: ClientsRepository.Blocking = mock[ClientsRepository.Blocking],
      connextHelper: ConnextHelper = mock[ConnextHelper],
      makerPaymentService: MakerPaymentService = mock[MakerPaymentService],
      preimagesRepository: PreimagesRepository.Blocking = mock[PreimagesRepository.Blocking],
      ethService: ETHService = mock[ETHService]
  )(f: (Peer, Peer) => T): T = {
    withPeers(
      feesEnabled,
      feesRepository,
      paymentService,
      orderFeesConfig,
      feeRefundsRepository,
      lnd,
      channelsRepository,
      tradesConfig,
      tradingPairsConfig,
      tradesRepository,
      reportsRepository,
      clientsRepository,
      connextHelper,
      makerPaymentService,
      preimagesRepository,
      ethService
    )("alice", "bob") {
      case TestData(alice :: bob :: Nil, _, _) => f(alice, bob)
    }
  }

  val xsnRHash: PaymentRHash =
    PaymentRHash.untrusted("cad85ebe120df49d4ebe96290226206f0a888524dc70b976df53cf8d0ebac161").value

  val xsnRHash2: PaymentRHash =
    PaymentRHash.untrusted("aad85ebe120df49d4ebe96290226206f0a888524dc70b976df53cf8d0ebac161").value

  val DEFAULT_ALLOWED_ORDERS = 10

  val defaultOrderFeesConfig = OrderFeesConfig(1.day)
  val defaultTradesConfig = TradesConfig(250.milliseconds)
  val defaultTradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)
  val defaultRetryConfig = RetryConfig(1.millisecond, 1.millisecond)

  val defaultChannelRentalConfig = ChannelRentalConfig(
    maxDuration = 7.days,
    minDuration = 1.hour,
    maxCapacityUsd = BigDecimal(10000),
    minCapacityUsd = BigDecimal(5),
    maxOnChainFeesUsd = BigDecimal(6),
    "hubAddress",
    Satoshis.from(BigDecimal("0.015")).value
  )

  private def newPeer(
      orderManager: OrderManagerActor.Ref,
      messageFilter: PeerMessageFilterActor.Ref,
      connectionManager: ConnectionManagerActor.Ref,
      name: String,
      paymentService: PaymentService,
      feesEnabled: Boolean,
      feesRepository: FeesRepository.Blocking,
      orderFeesConfig: OrderFeesConfig,
      feeRefundsRepository: FeeRefundsRepository.Blocking,
      lnd: MulticurrencyLndClient,
      channelsRepository: ChannelsRepository.Blocking,
      tradingPairsConfig: TradingPairsConfig,
      tradesRepository: TradesRepository.Blocking,
      reportsRepository: ReportsRepository.Blocking,
      clientsRepository: ClientsRepository.Blocking,
      connextHelper: ConnextHelper,
      makerPaymentService: MakerPaymentService,
      preimagesRepository: PreimagesRepository.Blocking,
      ethService: ETHService
  )(
      implicit system: ActorSystem
  ): Peer = {
    val client = TestProbe()

    val discordHelper: DiscordHelper = mock[DiscordHelper]
    val reportService = new reports.ReportsRepository.FutureImpl(reportsRepository)(Executors.databaseEC)
    val tradesRepositoryAsync = new TradesRepository.FutureImpl(tradesRepository)(Executors.databaseEC)
    val channelsRepositoryAsync = new channels.ChannelsRepository.FutureImpl(channelsRepository)(Executors.databaseEC)

    object explorerService extends ExplorerService {
      override def getUSDPrice(currency: Currency): Future[Either[ExplorerService.ExplorerErrors, BigDecimal]] =
        Future.successful(Right(1))

      override def getPrices(currency: Currency): Future[Either[ExplorerService.ExplorerErrors, CurrencyPrices]] =
        Future.successful(
          Right(CurrencyPrices(currency, BigDecimal(1), BigDecimal(1), Instant.now))
        )

      override def getTransactionFee(
          currency: Currency,
          transactionHash: String
      ): Future[Either[ExplorerService.ExplorerErrors, Satoshis]] = Future.successful(Right(Satoshis.One))

      override def getEstimateFee(currency: Currency): Future[Either[ExplorerService.ExplorerErrors, EstimatedFee]] =
        Future.successful(Right(EstimatedFee(currency.networkFee)))
    }

    val priceApi = new PriceApi(
      tradesRepositoryAsync,
      explorerService,
      defaultRetryConfig
    )(Executors.globalEC, system.scheduler)

    val currencyConverter = new CurrencyConverter(priceApi)
    val usdConverter = new UsdConverter(priceApi)
    val orderValidator = new OrderValidator(usdConverter, currencyConverter)(Executors.globalEC)

    val clientService = new ClientServiceImpl(
      new ClientsRepository.FutureImpl(clientsRepository)(Executors.databaseEC),
      usdConverter
    )

    val feeService = new LndFeeService(
      new FeesRepository.FutureImpl(feesRepository)(Executors.databaseEC),
      new FeeRefundsRepository.FutureImpl(feeRefundsRepository)(Executors.databaseEC),
      orderFeesConfig,
      paymentService,
      discordHelper,
      reportService,
      connextHelper,
      clientService,
      new PreimagesRepository.FutureImpl(preimagesRepository)(Executors.databaseEC)
    )

    val channelDepositMonitor = new ChannelDepositMonitor(
      ethService,
      connextHelper,
      channelsRepositoryAsync,
      defaultRetryConfig
    )(Executors.globalEC, system.scheduler)

    val channelService = new ChannelImp(
      lnd,
      channelsRepositoryAsync,
      discordHelper,
      reportService,
      defaultRetryConfig,
      explorerService,
      currencyConverter,
      usdConverter,
      defaultChannelRentalConfig,
      new ClientsRepository.FutureImpl(clientsRepository)(Executors.databaseEC),
      connextHelper,
      channelDepositMonitor,
      feeService,
      ethService
    )(Executors.globalEC, system.scheduler)

    val peerActorFactory = new PeerActor.Factory(
      orderManager,
      messageFilter,
      connectionManager,
      orderValidator,
      tradesRepositoryAsync,
      feeService,
      paymentService,
      FeatureFlags(feesEnabled = feesEnabled, rejectBlacklistedCountries = false),
      channelService,
      tradingPairsConfig,
      discordHelper,
      clientService,
      makerPaymentService,
      connextHelper,
      defaultChannelRentalConfig
    )

    val actor = peerActorFactory.build(
      client.ref,
      PeerUser.Wallet(id = ClientId.random(), name = "wallet user", DEFAULT_ALLOWED_ORDERS),
      Some(feesEnabled),
      Some(name)
    )

    Peer(client = client, actor = actor.actor)
  }
}

case class Peer(client: TestProbe, actor: ActorRef)
case class TestData(
    peers: List[Peer],
    orderManagerActor: OrderManagerActor.Ref,
    messageFilter: PeerMessageFilterActor.Ref
)
