package controllers

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.Materializer
import controllers.codecs.protobuf.{PeerCommandCodecs, PeerEventCodecs}
import controllers.validators.WebSocketValidator
import io.stakenet.orderbook.actors.connection.ConnectionManagerActor
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.peers.protocol.Command.InvalidCommand
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.peers.{PeerActor, PeerProxyActor, PeerUser}
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.models.AuthenticationInformation
import io.stakenet.orderbook.models.clients.ClientVersion
import io.stakenet.orderbook.protos.api
import io.stakenet.orderbook.services._
import javax.inject.{Inject, Singleton}
import javax.xml.bind.DatatypeConverter
import org.slf4j.LoggerFactory
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AbstractController, ControllerComponents, WebSocket}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class WebSocketController @Inject() (
    cc: ControllerComponents,
    peerActorFactory: PeerActor.Factory,
    messageFilter: PeerMessageFilterActor.Ref,
    override val tradingPairsConfig: TradingPairsConfig,
    clientService: ClientService,
    webSocketValidator: WebSocketValidator,
    connectionManager: ConnectionManagerActor.Ref,
    makerPaymentService: MakerPaymentService
)(implicit
    system: ActorSystem,
    mat: Materializer,
    ec: ExecutionContext
) extends AbstractController(cc)
    with PeerEventCodecs
    with PeerCommandCodecs {

  import WebSocketController._

  private val logger = LoggerFactory.getLogger(this.getClass)

  def ws(
      walletId: Option[String]
  ) = WebSocket.acceptOrResult[WebSocketIncomingMessage, WebSocketOutgoingMessage] { request =>
    // TODO: Move this to a handshake command, so that the clients can get meaningful error messages
    val lssdVersionHeader = request.headers
      .get("Client-Version") // custom header used by lssd
      .orElse(request.headers.get("Sec-WebSocket-Protocol")) // sub-protocol used by the web client
      .getOrElse("")
    val legacyClientVersion = Try(lssdVersionHeader.toInt).toOption
    val clientVersion = ClientVersion(lssdVersionHeader)
    val ipAddress = request.headers.get("X-Real-IP").getOrElse(request.remoteAddress)

    def flow(peerUser: PeerUser, overridesPaysFees: Option[Boolean]) = ActorFlow.actorRef { client =>
      val peerActor = peerActorFactory.build(client, peerUser, overridesPaysFees)

      PeerProxyActor.props(peerActor, messageFilter, peerUser)
    }

    val authenticationInformation = AuthenticationInformation(
      botMakerSecret = request.headers.get("BotMaker-Secret"),
      walletId = request.headers.get("Light-Wallet-Unique-Id").orElse(walletId),
      websocketSubprotocol = request.headers.get("Sec-WebSocket-Protocol")
    )

    for {
      client <- clientService.getUser(authenticationInformation)
      acceptedCountry <- webSocketValidator.acceptClientCountry(ipAddress)

      // TODO: Move this to a handshake command
      timeout = 3.seconds
      connected <- client
        .map { client =>
          connectionManager.ref.ask(ConnectionManagerActor.Command.Connect(client))(timeout).mapTo[Boolean]
        }
        .getOrElse(Future.successful(false))
    } yield (client, acceptedCountry) match {
      case (Some(user), true) =>
        val overridesPaysFees = user match {
          case PeerUser.Bot(_, paysFees, name, _) =>
            logger.info(s"Bot maker connected: $name")
            Some(paysFees)
          case _ =>
            None
        }

        val validVersion = user match {
          case _: PeerUser.Wallet => clientVersion.exists(_ >= LowestAcceptedClientVersion)
          case _ => true
        }

        if (!connected) {
          logger.info(s"client $user already has an open connection")
          Left(Forbidden(s"multiple connections per client are not allowed"))
        } else if (validVersion || legacyClientVersion.exists(_ >= LegacyLowestAcceptedClientVersion)) {
          // TODO: move this to the actor?
          user match {
            case bot: PeerUser.Bot =>
              makerPaymentService.retryFailedPayments(bot.id)
            case wallet: PeerUser.Wallet =>
              makerPaymentService.retryFailedPayments(wallet.id)

            case _ =>
              ()
          }

          logger.info(s"Accepting client, got version = $legacyClientVersion")
          Right(flow(user, overridesPaysFees))
        } else {
          logger.info(s"Rejecting client, got version = $lssdVersionHeader")
          Left(
            Forbidden(
              s"The client version is not accepted, oldest accepted version is $LegacyLowestAcceptedClientVersion, got = $lssdVersionHeader, be sure to submit the Client-Version header with the proper version"
            )
          )
        }

      case (None, _) =>
        logger.info(s"Client not found for $authenticationInformation")
        Left(Unauthorized(""))
      case (_, false) =>
        logger.info(s"rejecting client with ip address $ipAddress")
        Left(Unauthorized("Unauthorized Country"))
    }
  }

  // NOTE: the codecs depend on the tradingPairsConfig
  implicit def peerProtoTransformer(implicit
      commandCodec: CommandCodec,
      eventCodecs: EventCodec
  ): WebSocket.MessageFlowTransformer[WebSocketIncomingMessage, WebSocketOutgoingMessage] = {
    WebSocket.MessageFlowTransformer.byteArrayMessageFlowTransformer.map(
      input => {
        api.Command.validate(input) match {
          case Success(command) =>
            Try(commandCodec.decode(command)) match {
              case Success(message) => message
              case Failure(e) =>
                val hexInput = DatatypeConverter.printHexBinary(input)
                logger.info(s"Failed to decode the command: $command, hex: $hexInput", e)
                WebSocketIncomingMessage(command.clientMessageId, InvalidCommand(e.getMessage))
            }
          case Failure(e) =>
            val hexInput = DatatypeConverter.printHexBinary(input)
            logger.info(s"Failed to parse the command, hex: $hexInput", e)
            WebSocketIncomingMessage("", InvalidCommand(e.getMessage))
        }
      },
      output => eventCodecs.encode(output).toByteArray
    )
  }
}

object WebSocketController {

  val LegacyLowestAcceptedClientVersion = 2
  val LowestAcceptedClientVersion = ClientVersion("0.4.0.4").getOrElse(throw new RuntimeException("impossible"))

}
