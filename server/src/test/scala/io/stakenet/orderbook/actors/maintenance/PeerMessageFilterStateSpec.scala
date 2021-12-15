package io.stakenet.orderbook.actors.maintenance

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class PeerMessageFilterStateSpec
    extends TestKit(ActorSystem("PeerMessageFilterStateSpec"))
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "empty" should {
    "initialize inMaintenance to false" in {
      val state = PeerMessageFilterState.empty

      state.inMaintenance mustBe false
    }

    "initialize peers to an empty set" in {
      val state = PeerMessageFilterState.empty

      state.peers.isEmpty mustBe true
    }
  }

  "startMaintenance" should {
    "set inMaintenance to true" in {
      val state = PeerMessageFilterState.empty

      val result = state.startMaintenance

      result.inMaintenance mustBe true
    }
  }

  "stopMaintenance" should {
    "set inMaintenance to false" in {
      val state = PeerMessageFilterState.empty.startMaintenance

      val result = state.completeMaintenance

      result.inMaintenance mustBe false
    }
  }

  "+" should {
    "add peers to the peers set" in {
      val state = PeerMessageFilterState.empty
      val peer1 = TestProbe().ref
      val peer2 = TestProbe().ref
      val peer3 = TestProbe().ref

      val result = state + peer1 + peer2 + peer3

      result.peers mustBe Set(peer1, peer2, peer3)
    }
  }

  "-" should {
    "remove peers from the peers set" in {
      val state = PeerMessageFilterState.empty
      val peer1 = TestProbe().ref
      val peer2 = TestProbe().ref
      val peer3 = TestProbe().ref

      val result = state + peer1 + peer2 + peer3 - peer2

      result.peers mustBe Set(peer1, peer3)
    }
  }
}
