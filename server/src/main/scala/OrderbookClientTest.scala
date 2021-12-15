import akka.actor.ActorSystem
import com.github.andyglow.websocket._
import com.github.andyglow.websocket.util.Uri
import controllers.codecs.protobuf.{PeerCommandCodecs, PeerEventCodecs}
import io.netty.buffer.ByteBuf
import io.stakenet.orderbook.actors.peers.protocol._
import io.stakenet.orderbook.actors.peers.ws._
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.models.trading._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class OrderbookClient(url: String)(implicit actorSystem: ActorSystem) extends PeerCommandCodecs with PeerEventCodecs {

  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  var firstPong = true

  val wsHandler: PartialFunction[ByteBuf, Unit] = {
    case byteBuf: ByteBuf =>
      val bytes = Array.ofDim[Byte](byteBuf.readableBytes())
      byteBuf.readBytes(bytes)
      val proto = io.stakenet.orderbook.protos.api.Event.parseFrom(bytes)
      val msg = eventCodec.decode(proto)
      if (msg.event == Event.CommandResponse.PingResponse()) {
        if (firstPong) {
          firstPong = false
          println(s"<<| ${msg.event}, next ping responses won't be printed")
        }
      } else {
        println(s"<<| ${msg.event}")
      }

  }

  val cli = WebsocketClient[ByteBuf](
    uri = Uri(url),
    handler = WebsocketHandler(wsHandler),
    headers = Map("Client-Version" -> "10", "Light-Wallet-Unique-Id" -> "048d669299fba67ddbbcfa86fb3a344d0d3a5066")
  )

  val ws = cli.open()

  // schedule ping
  actorSystem.scheduler.scheduleAtFixedRate(1.second, 1.second) { () =>
    send(Command.Ping())
  }
  println("WS Connected")

  var firstPing = true

  def send(command: Command): Unit = {
    val msg = WebSocketIncomingMessage("test", command)
    if (command == Command.Ping()) {
      if (firstPing) {
        firstPing = false
        println(s"|>> ${msg.command}, next pings won't be printed")
      }
    } else {
      println(s"|>> ${msg.command}")
    }
    ws ! commandCodec.encode(msg).toByteArray
  }
}

object OrderbookClient {
  def prod(implicit actorSystem: ActorSystem) = new OrderbookClient("wss://orderbook.stakenet.io/api/ws")
  def staging(implicit actorSystem: ActorSystem) = new OrderbookClient("wss://stg-orderbook.stakenet.io/api/ws")
  def dev(implicit actorSystem: ActorSystem) = new OrderbookClient("ws://localhost:9000/ws")
}

object OrderbookClientTest {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem("repl")
    val client = OrderbookClient.prod
    client.send(Command.CleanTradingPairOrders(TradingPair.XSN_BTC))
  }
}
