Thread.currentThread.setContextClassLoader(getClass.getClassLoader) // get resources accessible on the console

import akka.actor.ActorSystem

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import controllers.codecs.protobuf.PeerCommandCodecs
import controllers.codecs.protobuf.PeerEventCodecs
import io.stakenet.orderbook.actors.peers.ws._
import io.stakenet.orderbook.actors.peers.protocol._
import com.github.andyglow.websocket._
import com.github.andyglow.websocket.util.Uri
import io.netty.buffer.ByteBuf
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.actors.peers.protocol.Command._
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.models.{OrderId, Satoshis}
import javax.xml.bind.DatatypeConverter

import scala.util.control.NonFatal

implicit val actorSystem = ActorSystem("repl")
lazy val prodWs = OrderbookClient.prod
lazy val stagingWs = OrderbookClient.staging
lazy val devWs = OrderbookClient.dev

object Helper extends PeerCommandCodecs with PeerEventCodecs {
  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  def parseHexCommand(hex: String): Unit = {
    val bytes = DatatypeConverter.parseHexBinary(hex)
    val proto = io.stakenet.orderbook.protos.api.Command.parseFrom(bytes)
    println(s"proto: ${proto.toProtoString}")
    try {
      val cmd = commandCodec.decode(proto)
      println(s"Command parsed: $cmd")
    } catch {
      case NonFatal(ex) =>
        println(s"Failed to parse command: ${ex.getMessage}")
        ex.printStackTrace()
    }
  }

  def placeBuyOrder(ws: OrderbookClient, pair: TradingPair, funds: BigDecimal, price: BigDecimal): Unit = {
    val order = TradingOrder.apply(pair)(
      pair.Order.limit(OrderSide.Buy, OrderId.random(),
        funds = Satoshis.from(funds).get,
        price = Satoshis.from(price).get))
    ws.send(Command.PlaceOrder(order, None))
  }
}

import Helper._
