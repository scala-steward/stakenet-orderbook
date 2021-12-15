package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.{CancelMatchedOrder, PlaceOrder}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CancelMatchedOrderResponse
import io.stakenet.orderbook.actors.peers.protocol.Event.ServerEvent.{MyMatchedOrderCanceled, MyOrderMatched}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.helpers.SampleOrders._
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.trading.TradingPair

class CancelMatchedOrderSpec extends PeerSpecBase("CancelMatchedOrderSpec") {
  "CancelMatchedOrder" should {
    TradingPair.values.foreach { pair =>
      s"cancel a $pair matched order" in {
        withTwoPeers() { (alice, bob) =>
          val aliceOrder = buyLimitOrder(pair, price = getSatoshis(100))
          val bobOrder = sellLimitOrder(pair, price = getSatoshis(100))

          alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, None))
          discardMsg(alice) // order placed

          bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, None))
          discardMsg(bob) // order matched

          val trade = alice.client.expectMsgPF() {
            case WebSocketOutgoingMessage(_, _, MyOrderMatched(trade, _)) => trade
          }

          alice.actor ! WebSocketIncomingMessage("id", CancelMatchedOrder(aliceOrder.id))
          alice.client.expectMsg(WebSocketOutgoingMessage(3, Some("id"), CancelMatchedOrderResponse(Some(trade))))
        }
      }
    }

    "notify the executing peer when the existing peer cancels the order" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = buyLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))
        val bobOrder = sellLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))

        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, None))
        discardMsg(alice) // order placed

        bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, None))
        discardMsg(bob) // order matched

        val trade = alice.client.expectMsgPF() {
          case WebSocketOutgoingMessage(_, _, MyOrderMatched(trade, _)) => trade
        }

        alice.actor ! WebSocketIncomingMessage("id", CancelMatchedOrder(aliceOrder.id))
        bob.client.expectMsg(WebSocketOutgoingMessage(2, None, MyMatchedOrderCanceled(trade)))
      }
    }

    "notify the existing peer when the executing peer cancels the order" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = buyLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))
        val bobOrder = sellLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))

        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, None))
        discardMsg(alice) // order placed

        bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, None))
        discardMsg(bob) // order matched

        val trade = alice.client.expectMsgPF() {
          case WebSocketOutgoingMessage(_, _, MyOrderMatched(trade, _)) => trade
        }

        bob.actor ! WebSocketIncomingMessage("id", CancelMatchedOrder(bobOrder.id))
        alice.client.expectMsg(WebSocketOutgoingMessage(3, None, MyMatchedOrderCanceled(trade)))
      }
    }

    "return None when cancelling an unknown matched order" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = buyLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))
        val bobOrder = sellLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))

        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, None))
        discardMsg(alice) // order placed

        bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, None))
        discardMsg(bob) // order matched
        discardMsg(alice) // order matched

        alice.actor ! WebSocketIncomingMessage("id", CancelMatchedOrder(OrderId.random()))
        alice.client.expectMsg(WebSocketOutgoingMessage(3, Some("id"), CancelMatchedOrderResponse(None)))
      }
    }

    "return None when cancelling a non matched order" in {
      withSinglePeer() { alice =>
        val order = buyLimitOrder(TradingPair.XSN_BTC, price = getSatoshis(100))

        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(order, None))
        discardMsg(alice)

        alice.actor ! WebSocketIncomingMessage("id", CancelMatchedOrder(order.id))
        alice.client.expectMsg(WebSocketOutgoingMessage(2, Some("id"), CancelMatchedOrderResponse(None)))
      }
    }
  }
}
