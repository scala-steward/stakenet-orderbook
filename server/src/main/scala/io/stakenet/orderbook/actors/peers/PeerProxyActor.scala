package io.stakenet.orderbook.actors.peers

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.peers.ws.WebSocketIncomingMessage

/** Acts as a proxy for a peer actor, all messages sent to a peer are received first by this actor first which its only
  * purpose is to redirect the messages to the message filter for processing before they actually reach the peer actor
  *
  * @param peer
  *   the peer for which this is acting as a proxy
  * @param messageFilter
  *   a filter that process messages before they reach the peer it was sent to
  * @param user
  *   the user associated with this proxy's peer
  */
class PeerProxyActor(peer: PeerActor.Ref, messageFilter: PeerMessageFilterActor.Ref, user: PeerUser)
    extends Actor
    with ActorLogging {

  /** TODO: Find a better way, this is not a generic proxy because its killing it's underlying actor
    *
    * The reason to kill the peer is to let it know about the ws disconnection event, it's not a perfect solution but
    * should fix the big problem related to phantom orders.
    */
  override def postStop(): Unit = {
    peer.actor ! PoisonPill
  }

  override def receive: Receive = {
    case message: WebSocketIncomingMessage => messageFilter.ref ! PeerMessage(peer.actor, message)
    case message => log.info(s"${user.name} sent an unexpected message: $message")
  }
}

object PeerProxyActor {

  def props(peer: PeerActor.Ref, messagesManager: PeerMessageFilterActor.Ref, user: PeerUser): Props = {
    Props(new PeerProxyActor(peer, messagesManager, user))
  }
}
