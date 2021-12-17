package io.stakenet.orderbook.actors.connection

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import io.stakenet.orderbook.actors.connection.ConnectionManagerActor.Command._
import io.stakenet.orderbook.actors.peers.PeerUser

/** Managed clients connected through a websocket
  *
  *   - Wallet and Bot clients can only have one connection open at a time, attempts to open more connections will be
  *     rejected until current connections is closed
  *   - For Web clients there is no connection limit
  *
  * TODO: Move the single connection validation to a Handshake command that also validates a client's location and a
  * wallet client's version
  */
class ConnectionManagerActor extends Actor with ActorLogging {

  override def receive: Receive = { case message =>
    log.info(s"Unexpected message: $message")
  }

  override def preStart(): Unit = {
    log.info("PeerMessageFilter starting")
    context.become(withState(ConnectionManagerState.empty))
  }

  private def withState(state: ConnectionManagerState): Receive = {

    case Connect(client) =>
      if (state.exists(client)) {
        sender() ! false
      } else {
        context.become(withState(state + client))
        sender() ! true
      }

    case Disconnect(client) =>
      context.become(withState(state - client))

    case message =>
      log.info(s"Unexpected message: $message")
  }
}

object ConnectionManagerActor {

  def props(): Props = {
    Props(new ConnectionManagerActor())
  }

  final class Ref private (val ref: ActorRef)

  object Ref {

    def apply(name: String = "connection-manager")(implicit system: ActorSystem): Ref = {
      val actor = system.actorOf(props(), name)
      new Ref(actor)
    }
  }

  sealed trait Command extends Product with Serializable

  object Command {
    final case class Connect(peerUser: PeerUser) extends Command
    final case class Disconnect(peerUser: PeerUser) extends Command
  }
}
