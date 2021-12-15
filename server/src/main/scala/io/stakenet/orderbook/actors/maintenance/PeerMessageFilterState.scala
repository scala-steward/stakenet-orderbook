package io.stakenet.orderbook.actors.maintenance

import akka.actor.ActorRef

case class PeerMessageFilterState(inMaintenance: Boolean, peers: Set[ActorRef]) {

  def startMaintenance: PeerMessageFilterState = copy(inMaintenance = true)

  def completeMaintenance: PeerMessageFilterState = copy(inMaintenance = false)

  def +(peer: ActorRef): PeerMessageFilterState = copy(peers = peers + peer)

  def -(peer: ActorRef): PeerMessageFilterState = copy(peers = peers - peer)
}

object PeerMessageFilterState {

  def empty: PeerMessageFilterState = {
    PeerMessageFilterState(false, Set.empty)
  }
}
