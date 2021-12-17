package io.stakenet.orderbook.actors.maintenance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor.Command._
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor.{
  CompleteMaintenanceResponse,
  StartMaintenanceResponse
}
import io.stakenet.orderbook.actors.peers.PeerMessage
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CommandFailed
import io.stakenet.orderbook.actors.peers.protocol.Event.ServerEvent
import io.stakenet.orderbook.actors.peers.protocol.TaggedCommandResponse

/** Filters messages sent to the peer actor
  *
  *   - if server maintenance is active when a message is received the message is discarded and ServerInMaintenance is
  *     sent to the peer actor instead.
  *   - if server maintenance is not active then the message is sent to the peer actor as received
  *
  * This actor also has a list of active peers and notifies them when server maintenance is started/completed
  */
class PeerMessageFilterActor extends Actor with ActorLogging {

  override def receive: Receive = { case message =>
    log.info(s"Unexpected message: $message")
  }

  override def preStart(): Unit = {
    log.info("PeerMessageFilter starting")
    context.become(withState(PeerMessageFilterState.empty))
  }

  private def withState(state: PeerMessageFilterState): Receive = {

    case PeerConnected(peer) =>
      if (state.inMaintenance) {
        peer ! ServerEvent.MaintenanceInProgress()
      }
      context.become(withState(state + peer))

    case PeerDisconnected(peer) =>
      context.become(withState(state - peer))

    case StartMaintenance() =>
      if (state.inMaintenance) {
        sender() ! StartMaintenanceResponse.MaintenanceAlreadyInProgress()
      } else {
        log.info("Maintenance started")
        state.peers.foreach(_ ! ServerEvent.MaintenanceInProgress())
        sender() ! StartMaintenanceResponse.MaintenanceStarted()
        context.become(withState(state.startMaintenance))
      }

    case CompleteMaintenance() =>
      if (state.inMaintenance) {
        log.info("Maintenance completed")
        state.peers.foreach(_ ! ServerEvent.MaintenanceCompleted())
        sender() ! CompleteMaintenanceResponse.MaintenanceCompleted()
        context.become(withState(state.completeMaintenance))
      } else {
        sender() ! CompleteMaintenanceResponse.NoMaintenanceInProgress()
      }

    case PeerMessage(peer, message) =>
      if (state.inMaintenance) {
        peer ! TaggedCommandResponse(message.clientMessageId, CommandFailed.ServerInMaintenance())
      } else {
        peer ! message
      }

    case message =>
      log.info(s"Unexpected message: $message")
  }
}

object PeerMessageFilterActor {

  def props(): Props = {
    Props(new PeerMessageFilterActor())
  }

  final class Ref private (val ref: ActorRef)

  object Ref {

    def apply(name: String = "peer-message-filter")(implicit system: ActorSystem): Ref = {
      val actor = system.actorOf(props(), name)
      new Ref(actor)
    }
  }

  sealed trait Command extends Product with Serializable

  object Command {
    final case class PeerConnected(peer: ActorRef) extends Command
    final case class PeerDisconnected(peer: ActorRef) extends Command
    final case class StartMaintenance() extends Command
    final case class CompleteMaintenance() extends Command
  }

  sealed trait StartMaintenanceResponse

  object StartMaintenanceResponse {
    final case class MaintenanceStarted() extends StartMaintenanceResponse
    final case class MaintenanceAlreadyInProgress() extends StartMaintenanceResponse
  }

  sealed trait CompleteMaintenanceResponse

  object CompleteMaintenanceResponse {
    final case class MaintenanceCompleted() extends CompleteMaintenanceResponse
    final case class NoMaintenanceInProgress() extends CompleteMaintenanceResponse
  }
}
