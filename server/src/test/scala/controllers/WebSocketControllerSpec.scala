package controllers

import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import java.util.function.Consumer

import akka.pattern.ask
import akka.util.Timeout
import controllers.codecs.protobuf.{PeerCommandCodecs, PeerEventCodecs, WebSocket}
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers
import io.stakenet.orderbook.actors.peers.protocol.Command.{GetTradingPairs, PlaceOrder}
import io.stakenet.orderbook.actors.peers.protocol.Event
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse._
import io.stakenet.orderbook.actors.peers.protocol.Event.ServerEvent.MaintenanceInProgress
import io.stakenet.orderbook.actors.peers.results.PlaceOrderResult.{OrderPlaced, OrderRejected}
import io.stakenet.orderbook.actors.peers.ws.WebSocketIncomingMessage
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.helpers.{CustomMatchers, SampleOrders}
import io.stakenet.orderbook.models.WalletId
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.modules.{DiscordModule, TasksModule}
import io.stakenet.orderbook.protos
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.makerPayments.MakerPaymentsRepository
import io.stakenet.orderbook.services.{IpInfoErrors, IpInfoService}
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._
import org.scalatest.concurrent.Eventually._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play._
import play.api.db.{DBApi, Database, Databases}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{Helpers, TestServer, WsTestClient}
import play.api.{Application, Configuration, Environment, Mode}
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import play.shaded.ahc.org.asynchttpclient.netty.ws.NettyWebSocket

import scala.compat.java8.FutureConverters
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class WebSocketControllerSpec extends PlaySpec {

  import WebSocketControllerSpec._

  "WebSocketController" should {
    "allow to subscribe to a currency" in {
      withClients(clients = List(walletUserHeaders)) { case Right(alice) :: Nil =>
        alice.sendCommand(peers.protocol.Command.Subscribe(TradingPair.LTC_BTC, retrieveOrdersSummary = false))
        alice.expectEvent() must be(
          peers.protocol.Event.CommandResponse.SubscribeResponse(TradingPair.LTC_BTC, List.empty, List.empty)
        )
      }
    }

    "Fails on malformed command" in {
      withClients(clients = List(walletUserHeaders)) { case Right(alice) :: Nil =>
        val malformedCommand = Array[Byte](0x52, 0x61, 0x75, 0x6c)
        alice.ws.sendBinaryFrame(malformedCommand)
        alice.expectEvent() must be(
          peers.protocol.Event.CommandResponse.CommandFailed(
            "While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either that the input has been truncated or that an embedded message misreported its own length."
          )
        )
      }
    }

    "Fails on unknown command" in {
      withClients(clients = List(walletUserHeaders)) { case Right(alice) :: Nil =>
        val unknownCommand = Array[Byte](10, 2, 105, 100, -126, -126, -126, 100, 0)
        alice.ws.sendBinaryFrame(unknownCommand)
        alice.expectEvent() must be(peers.protocol.Event.CommandResponse.CommandFailed("Missing command"))
      }
    }

    "Remove peer orders on client disconnection" in {
      val botMakerSecret = "vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"
      val headers = Map("BotMaker-Secret" -> botMakerSecret)
      val clientsRepository = mock[ClientsRepository.Blocking]

      when(clientsRepository.findBotMakerClient(botMakerSecret)).thenReturn(
        Some(BotMakerClient("bot1.xsnbot.com", ClientId.random(), false))
      )

      withClientsAndApp(clients = List(headers), clientsRepository = clientsRepository) {
        case (app, Right(botMaker) :: Nil) =>
          val order = SampleOrders.XSN_LTC_BUY_LIMIT_1
          botMaker.sendCommand(PlaceOrder(order, None))

          val _ = botMaker.expectEvent()
          botMaker.close()
          val orderManager = app.actorSystem.actorSelection("/user/order-manager")
          implicit val timeout = Timeout(10.seconds)
          val orders = orderManager
            .ask(OrderManagerActor.Command.GetAllOrders)
            .mapTo[OrderManagerActor.Event.OrdersRetrieved]
            .futureValue
            .orders
          orders.isEmpty must be(true)
      }
    }

    "Allow BotMaker with disabled fees to put an order without a fee" in {
      val botMakerSecret = "vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"
      val headers = Map("BotMaker-Secret" -> botMakerSecret)
      val clientsRepository = mock[ClientsRepository.Blocking]

      when(clientsRepository.findBotMakerClient(botMakerSecret)).thenReturn(
        Some(BotMakerClient("bot1.xsnbot.com", ClientId.random(), false))
      )

      withClients(clients = List(headers), clientsRepository = clientsRepository) { case Right(botMaker) :: Nil =>
        val order = SampleOrders.XSN_LTC_BUY_LIMIT_1
        botMaker.sendCommand(PlaceOrder(order, None))

        botMaker.expectEvent() match {
          case response: Event.CommandResponse.PlaceOrderResponse =>
            response.result match {
              case OrderPlaced(orderReceived) => CustomMatchers.matchOrderIgnoreId(orderReceived, order)
              case _ => fail("Invalid event received")
            }
          case _ => fail("Invalid event received")
        }
      }
    }

    "Not allow BotMaker with enabled fees to put an order without a fee" in {
      val botMakerSecret = "AHesBFKCL1JEtKyu3lBIPRP12qbN4tuTzlJ0isZQnP"
      val headers = Map("BotMaker-Secret" -> botMakerSecret)
      val clientsRepository = mock[ClientsRepository.Blocking]

      when(clientsRepository.findBotMakerClient(botMakerSecret)).thenReturn(
        Some(BotMakerClient("Azuki", ClientId.random(), true))
      )

      withClients(clients = List(headers), clientsRepository = clientsRepository) { case Right(botMaker) :: Nil =>
        val order = SampleOrders.XSN_LTC_BUY_LIMIT_1
        botMaker.sendCommand(PlaceOrder(order, None))

        val error = "A fee is required but no payment was provided"
        botMaker.expectEvent() mustBe PlaceOrderResponse(OrderRejected(error))
      }
    }

    "get available trading pairs with fees on" in {
      val botMakerSecret = "vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"
      val headers = Map("BotMaker-Secret" -> botMakerSecret)
      val clientsRepository = mock[ClientsRepository.Blocking]

      when(clientsRepository.findBotMakerClient(botMakerSecret)).thenReturn(
        Some(BotMakerClient("bot1.xsnbot.com", ClientId.random(), true))
      )

      withClients(clients = List(headers), clientsRepository = clientsRepository) { case Right(alice) :: Nil =>
        alice.sendCommand(GetTradingPairs())

        alice.expectEvent() match {
          case GetTradingPairsResponse(tradingPairs, true) =>
            tradingPairs.sorted mustBe TradingPair.values.toList.sorted
          case event => fail(s"Unexpected response: $event")
        }
      }
    }

    "get available trading pairs with fees off" in {
      val botMakerSecret = "vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD"
      val headers = Map("BotMaker-Secret" -> botMakerSecret)
      val clientsRepository = mock[ClientsRepository.Blocking]

      when(clientsRepository.findBotMakerClient(botMakerSecret)).thenReturn(
        Some(BotMakerClient("bot1.xsnbot.com", ClientId.random(), false))
      )

      withClients(clients = List(headers), clientsRepository = clientsRepository) { case Right(alice) :: Nil =>
        alice.sendCommand(GetTradingPairs())

        alice.expectEvent() match {
          case GetTradingPairsResponse(tradingPairs, false) =>
            tradingPairs.sorted mustBe TradingPair.values.toList.sorted
          case event => fail(s"Unexpected response: $event")
        }
      }
    }

    "Return ServerInMaintenance when server is in maintenance" in {
      withClients(clients = List(walletUserHeaders), inMaintenance = true) { case Right(alice) :: Nil =>
        alice.sendCommand(GetTradingPairs())

        (alice.expectEvent(), alice.expectEvent()) match {
          case (MaintenanceInProgress(), CommandFailed.ServerInMaintenance()) => succeed
          case events => fail(s"Unexpected response: $events")
        }
      }
    }

    "reject an anonymous user" in {
      withClients(clients = List(emptyHeaders)) {
        case Left(401) :: Nil =>
          succeed
        case client :: Nil =>
          fail(s"Expected 401 status code got: $client")
      }
    }

    "reject a second connection from the same client" in {
      withClients(clients = List(walletUserHeaders, walletUserHeaders)) {
        case Right(_) :: Left(403) :: Nil =>
          succeed
        case _ =>
          fail(s"Expected to accept first connection and reject second connection")
      }
    }

    "allow client to connect after closing the first connection" in {
      withClients(clients = List(walletUserHeaders)) {
        case Right(_) :: Nil =>
          succeed
        case Left(error) :: Nil =>
          fail(s"Expected connection to be successfull, got error: $error")
      }

      withClients(clients = List(walletUserHeaders)) {
        case Right(_) :: Nil =>
          succeed
        case Left(error) :: Nil =>
          fail(s"Expected connection to be successfull, got error: $error")
      }
    }

    "allow client to authenticate using query params" in {
      val port: Int = Helpers.testServerPort
      val clientsRepository = mock[ClientsRepository.Blocking]
      val makerPaymentsRepository = mock[MakerPaymentsRepository.Blocking]
      val app = applicationBuilder(clientsRepository, makerPaymentsRepository).build()

      when(clientsRepository.findWalletClient(defaultWalletId)).thenReturn(Some(defaultWalletClient))

      Helpers.running(TestServer(port, app)) {
        val myPublicAddress = s"localhost:$port"
        val serverURL = s"ws://$myPublicAddress/ws?walletId=$defaultWalletId"
        val client = createWSClient()
        val asyncHttpClient: AsyncHttpClient = client.underlying[AsyncHttpClient]
        val webSocketClient = new WebSocketClient(asyncHttpClient)
        val queue = new ArrayBlockingQueue[Array[Byte]](10)
        val consumer: Consumer[Array[Byte]] = (message: Array[Byte]) => queue.put(message)
        val listener = new WebSocketClient.LoggingListener(consumer)
        val headers = Map(
          "Origin" -> serverURL,
          "Client-Version" -> WebSocketController.LegacyLowestAcceptedClientVersion.toString
        )
        val completionStage = webSocketClient.call(serverURL, headers.asJava, listener)

        val webSocket = FutureConverters.toScala(completionStage).futureValue
        eventually {
          if (listener.getThrowable == null && (!webSocket.isOpen || !webSocket.isReady)) {
            throw new RuntimeException("Not ready")
          }
        }

        val errorCode = Option(listener.getThrowable).map { error =>
          val errorPattern = "Invalid Status code=([0-9]{3}) text=[a-zA-Z]+".r

          error.getMessage match {
            case errorPattern(code) => code.toInt
            case _ => throw error
          }
        }

        val result = errorCode.toLeft(WebSocket(client, ActiveWebSocket(webSocket, queue)))

        result.isRight mustBe true
      }
    }
  }
}

object WebSocketControllerSpec extends PeerCommandCodecs with PeerEventCodecs {

  implicit val eventuallyPatienceConfig: Eventually.PatienceConfig = Eventually.PatienceConfig(5.seconds, 300.millis)
  implicit val customPatienceConfig: ScalaFutures.PatienceConfig = ScalaFutures.PatienceConfig(5.seconds, 300.millis)
  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  val protoCommandCodec = implicitly[CommandCodec]
  val protoEventCodec = implicitly[EventCodec]

  case class ActiveWebSocket(ws: NettyWebSocket, incoming: ArrayBlockingQueue[Array[Byte]]) {

    def close(): Unit = {
      val _ = ws.sendCloseFrame.get()
    }

    def sendCommand(cmd: peers.protocol.Command) = {
      val msg = WebSocketIncomingMessage("id", cmd)
      val proto = protoCommandCodec.encode(msg)

      ws.sendBinaryFrame(proto.toByteArray)
    }

    def expectEvent(): peers.protocol.Event = {
      val bytes = incoming.poll(10, TimeUnit.SECONDS)
      if (bytes == null) {
        throw new RuntimeException("Expecting an event, got not bytes at all")
      }

      val proto = protos.api.Event.parseFrom(bytes)
      val event = protoEventCodec.decode(proto)
      println(s"Got bytes = ${bytes.toList.mkString("{", ", ", "]")}, event = $event")
      event.event
    }
  }

  private def createWSClient(implicit
      port: play.api.http.Port = new play.api.http.Port(-1),
      scheme: String = "http"
  ) = {
    new WsTestClient.InternalWSClient(scheme, port.value)
  }

  /** A dummy [[Database]] and [[DBApi]] just to allow a play application to start without connecting to a real database
    * from application.conf.
    */
  private val dummyDB = Databases.inMemory()
  private val dummyDBApi = new DBApi {
    override def databases(): Seq[Database] = List(dummyDB)
    override def database(name: String): Database = dummyDB
    override def shutdown(): Unit = dummyDB.shutdown()
  }

  /** Loads configuration disabling evolutions on default database.
    *
    * This allows to not write a custom application.conf for testing and ensure play evolutions are disabled.
    */
  private def loadConfigWithoutEvolutions(env: Environment): Configuration = {
    val map = Map("play.evolutions.db.default.enabled" -> false)

    Configuration.from(map).withFallback(Configuration.load(env))
  }

  private val emptyHeaders = Map[String, String]()
  private val defaultWalletId = WalletId("048d669299fba67ddbbcfa86fb3a344d0d3a5066").value
  private val defaultWalletClient = WalletClient(defaultWalletId, ClientId.random())
  private val walletUserHeaders = Map("Light-Wallet-Unique-Id" -> defaultWalletId.toString)

  private val dummyIpInfo = new IpInfoService {
    override def getCountry(ip: String): Future[Either[IpInfoErrors, String]] = Future.successful(Right("MX"))
  }

  private def applicationBuilder(
      clientsRepository: ClientsRepository.Blocking,
      makerPaymentsRepository: MakerPaymentsRepository.Blocking
  ) = {
    GuiceApplicationBuilder(loadConfiguration = loadConfigWithoutEvolutions)
      .in(Mode.Test)
      .disable(classOf[TasksModule])
      .disable(classOf[DiscordModule])
      .overrides(bind[Database].to(dummyDB))
      .overrides(bind[DBApi].to(dummyDBApi))
      .overrides(bind[IpInfoService].to(dummyIpInfo))
      .overrides(bind[ClientsRepository.Blocking].to(clientsRepository))
      .overrides(bind[MakerPaymentsRepository.Blocking].to(makerPaymentsRepository))
  }

  private def withClientsAndApp[T](
      clients: List[Map[String, String]],
      clientsRepository: ClientsRepository.Blocking,
      makerPaymentsRepository: MakerPaymentsRepository.Blocking = mock[MakerPaymentsRepository.Blocking],
      inMaintenance: Boolean = false
  )(
      f: PartialFunction[(Application, List[Either[Int, ActiveWebSocket]]), T]
  ): T = {
    lazy val port: Int = Helpers.testServerPort
    val app = applicationBuilder(clientsRepository, makerPaymentsRepository).build()

    when(clientsRepository.findWalletClient(defaultWalletId)).thenReturn(Some(defaultWalletClient))

    Helpers.running(TestServer(port, app)) {
      val myPublicAddress = s"localhost:$port"
      val serverURL = s"ws://$myPublicAddress/ws"
      val defaultHeaders = Map(
        "Origin" -> serverURL,
        "Client-Version" -> WebSocketController.LegacyLowestAcceptedClientVersion.toString
      )

      val webSockets = clients.map(defaultHeaders ++ _).map(createWebSocket)
      try {
        val params = webSockets.map(_.map(_.getSocket))

        if (inMaintenance) {
          val messagesManager = app.actorSystem.actorSelection("/user/peer-message-filter")
          messagesManager ! PeerMessageFilterActor.Command.StartMaintenance()
        }

        f(app -> params)
      } finally {
        webSockets.foreach(_.foreach(_.close()))
      }
    }
  }

  private def withClients[T](
      clients: List[Map[String, String]],
      clientsRepository: ClientsRepository.Blocking = mock[ClientsRepository.Blocking],
      makerPaymentsRepository: MakerPaymentsRepository.Blocking = mock[MakerPaymentsRepository.Blocking],
      inMaintenance: Boolean = false
  )(
      f: PartialFunction[List[Either[Int, ActiveWebSocket]], T]
  ): T = {
    withClientsAndApp(clients, clientsRepository, makerPaymentsRepository, inMaintenance) { case (_, list) =>
      f(list)
    }
  }

  private def createWebSocket(headers: Map[String, String]): Either[Int, WebSocket] = {
    lazy val port: Int = Helpers.testServerPort
    val myPublicAddress = s"localhost:$port"
    val serverURL = s"ws://$myPublicAddress/ws"
    val client = createWSClient()
    val asyncHttpClient: AsyncHttpClient = client.underlying[AsyncHttpClient]
    val webSocketClient = new WebSocketClient(asyncHttpClient)
    val queue = new ArrayBlockingQueue[Array[Byte]](10)
    val consumer: Consumer[Array[Byte]] = (message: Array[Byte]) => queue.put(message)
    val listener = new WebSocketClient.LoggingListener(consumer)
    val completionStage = webSocketClient.call(serverURL, headers.asJava, listener)

    val webSocket = FutureConverters.toScala(completionStage).futureValue
    eventually {
      if (listener.getThrowable == null && (!webSocket.isOpen || !webSocket.isReady)) {
        throw new RuntimeException("Not ready")
      }
    }

    val errorCode = Option(listener.getThrowable).map { error =>
      val errorPattern = "Invalid Status code=([0-9]{3}) text=[a-zA-Z]+".r

      error.getMessage match {
        case errorPattern(code) => code.toInt
        case _ => throw error
      }
    }

    errorCode.toLeft(WebSocket(client, ActiveWebSocket(webSocket, queue)))
  }
}
