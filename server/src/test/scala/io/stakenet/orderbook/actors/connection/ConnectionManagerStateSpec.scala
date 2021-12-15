package io.stakenet.orderbook.actors.connection

import akka.actor.ActorSystem
import akka.testkit.TestKit
import io.stakenet.orderbook.actors.peers.PeerUser
import io.stakenet.orderbook.models.clients.ClientId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ConnectionManagerStateSpec
    extends TestKit(ActorSystem("ConnectionManagerStateSpec"))
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "empty" should {
    "initialize clients to an empty set" in {
      val state = ConnectionManagerState.empty

      state.clients mustBe empty
    }
  }

  "+" should {
    "add clients to the clients set" in {
      val state = ConnectionManagerState.empty
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook

      val result = state + client1 + client2 + client3

      result.clients mustBe Set(client1, client2, client3)
    }
  }

  "-" should {
    "remove peers from the peers set" in {
      val state = ConnectionManagerState.empty
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook

      val result = state + client1 + client2 + client3 - client2

      result.clients mustBe Set(client1, client3)
    }
  }

  "exists" should {
    "should return true when wallet client exists" in {
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook
      val state = ConnectionManagerState.empty + client1 + client2 + client3

      state.exists(client1) mustBe true
    }

    "should return false when wallet client does not exists" in {
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook
      val state = ConnectionManagerState.empty + client2 + client3

      state.exists(client1) mustBe false
    }

    "should return true when bot client exists" in {
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook
      val state = ConnectionManagerState.empty + client1 + client2 + client3

      state.exists(client2) mustBe true
    }

    "should return false when bot client does not exists" in {
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook
      val state = ConnectionManagerState.empty + client1 + client3

      state.exists(client2) mustBe false
    }

    "should return false for web clients" in {
      val client1 = PeerUser.Wallet(ClientId.random(), "wallet", 10)
      val client2 = PeerUser.Bot(ClientId.random(), paysFees = true, "bot", 100)
      val client3 = PeerUser.WebOrderbook
      val state = ConnectionManagerState.empty + client1 + client2 + client3

      state.exists(client3) mustBe false
    }
  }
}
