package io.stakenet.orderbook.actors.peers

import java.time.Instant

import akka.actor.PoisonPill
import io.stakenet.lssd.protos.swap_packets.{Packet, SwapCompletePacketBody}
import io.stakenet.orderbook.actors.peers.protocol.Command.{PlaceOrder, SendOrderMessage}
import io.stakenet.orderbook.actors.peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled
import io.stakenet.orderbook.actors.peers.results.PlaceOrderResult
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.{PeerSpecBase, peers}
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.trading.TradingPair.{XSN_BTC, XSN_LTC}
import io.stakenet.orderbook.models.trading.{OrderSide, TradingOrder}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.repositories.fees.requests.BurnFeeRequest
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.validators.Fees
import io.stakenet.orderbook.services.{MakerPaymentService, PaymentService}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito
import org.mockito.MockitoSugar.{mock, verify, when}
import org.scalatest.OptionValues._

import scala.concurrent.Future

class PlaceOrderSpec extends PeerSpecBase("PlaceOrderSpec") {

  import io.stakenet.orderbook.helpers.SampleOrders._

  val XSN_LTC_MARKET_ORDER: TradingOrder =
    XSN_LTC.MarketOrder(OrderId.random(), trading.OrderSide.Sell, funds = getSatoshis(10))

  "PlaceOrder" should {
    "not lock a fee to an market order that was rejected" in pending
    "accept a rhash as fee" in pending

    "reject new orders when reaching the allowed limit" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        def newOrder = XSN_LTC_BUY_LIMIT_1
        def expectedSuccessMsg(counter: Long, order: TradingOrder) = {
          peers.ws.WebSocketOutgoingMessage(
            counter,
            Some(requestId),
            peers.protocol.Event.CommandResponse.PlaceOrderResponse(
              PlaceOrderResult.OrderPlaced(order)
            )
          )
        }

        (1 to DEFAULT_ALLOWED_ORDERS).foreach { index =>
          val order = newOrder
          alice.actor ! peers.ws
            .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
          alice.client.expectMsg(expectedSuccessMsg(index.toLong, order))
        }

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(newOrder, Some(xsnRHash)))

        val expectedRejectedMsg = peers.ws.WebSocketOutgoingMessage(
          DEFAULT_ALLOWED_ORDERS + 1L,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"You aren't allowed to place more than $DEFAULT_ALLOWED_ORDERS orders, if you need to do so, contact the admins"
            )
          )
        )
        alice.client.expectMsg(expectedRejectedMsg)
      }
    }

    "removing orders affects the allowed limit" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        def newOrder = XSN_LTC_BUY_LIMIT_1
        def expectedSuccessMsg(counter: Long, order: TradingOrder) = {
          peers.ws.WebSocketOutgoingMessage(
            counter,
            Some(requestId),
            peers.protocol.Event.CommandResponse.PlaceOrderResponse(
              PlaceOrderResult.OrderPlaced(order)
            )
          )
        }

        (1 to DEFAULT_ALLOWED_ORDERS).foreach { index =>
          val order = newOrder
          alice.actor ! peers.ws
            .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
          alice.client.expectMsg(expectedSuccessMsg(index.toLong, order))

          if (index == DEFAULT_ALLOWED_ORDERS) {
            alice.actor ! peers.ws
              .WebSocketIncomingMessage(requestId, peers.protocol.Command.CancelOpenOrder(order.value.id))
            discardMsg(alice)
          }
        }

        val lastOrder = newOrder
        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(lastOrder, Some(xsnRHash)))

        alice.client.expectMsg(expectedSuccessMsg(DEFAULT_ALLOWED_ORDERS + 2L, lastOrder))
      }
    }

    "reject a market order when it can't be matched immediately" in {
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(tradesRepository = tradesRepository) { alice =>
        val requestId = "id"
        val order = XSN_LTC_MARKET_ORDER
        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected("There aren't orders to fulfill your market order, try later")
          )
        )

        when(tradesRepository.getLastPrice(order.pair)).thenReturn(None)

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
        alice.client.expectMsg(expected)
      }
    }

    "reject to place an order when you haven't paid enough" in {
      pending // TODO: Fix me

      val paymentService = mock[PaymentService]
      withSinglePeer(paymentService = paymentService) { alice =>
        val requestId = "id"
        val paymentHash = PaymentRHash.untrusted("a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6")
        val order = XSN_LTC_BUY_LIMIT_1

        val payingCurrency = Fees.getCurrencyPayment(order)
        when(paymentService.validatePayment(payingCurrency, paymentHash.value)).thenReturn(
          Future.successful(PaymentData(Satoshis.Zero, Instant.now()))
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.CommandFailed("Invalid paymentHash")
        )
        alice.actor ! peers.ws
          .WebSocketIncomingMessage(
            requestId,
            peers.protocol.Command
              .PlaceOrder(
                order,
                paymentHash
              )
          )
        alice.client.expectMsg(expected)
      }
    }

    "allow alice to place a limit order" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_LTC_BUY_LIMIT_1
        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(order)
          )
        )
        alice.actor ! peers.ws
          .WebSocketIncomingMessage(
            requestId,
            peers.protocol.Command
              .PlaceOrder(order, Some(xsnRHash))
          )
        alice.client.expectMsg(expected)
      }
    }

    "allow bob to place the same order as alice" in {
      withTwoPeers(false) { (alice, bob) =>
        val requestId = "id"
        val order = XSN_BTC_SELL_LIMIT_1

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(order)
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
        alice.client.expectMsg(expected)

        bob.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
        bob.client.expectMsg(expected)
      }
    }

    "reject bob to place an order when the payment hash is locked on another order" in {
      pending // TODO: Fix me
      withTwoPeers(false) { (_, bob) =>
        val requestId = "id"
        val order = XSN_BTC_BUY_LIMIT_1

        val expected =
          peers.ws.WebSocketOutgoingMessage(
            1,
            Some(requestId),
            peers.protocol.Event.CommandResponse.CommandFailed("The payment hash has been used before")
          )

        bob.actor ! peers.ws
          .WebSocketIncomingMessage(
            requestId,
            peers.protocol.Command.PlaceOrder(order, Some(xsnRHash))
          )
        bob.client.expectMsg(expected)
      }
    }

    "reject a market order when there are only orders from same client" in {
      val tradesRepository = mock[TradesRepository.Blocking]

      withSinglePeer(tradesRepository = tradesRepository) { alice =>
        val requestId = "id"
        val order = XSN_BTC_BUY_LIMIT_1
        val order2 = XSN_BTC_SELL_MARKET

        val expected = peers.ws.WebSocketOutgoingMessage(
          2,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected("Your order was matched with one of your own orders")
          )
        )

        when(tradesRepository.getLastPrice(order.pair)).thenReturn(None)
        when(tradesRepository.getLastPrice(order2.pair)).thenReturn(None)

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
        discardMsg(alice)

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order2, Some(xsnRHash)))
        alice.client.expectMsg(expected)

      }
    }

    "reject a limit order when there are only orders from same client that could get matched" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC_BUY_LIMIT_1
        val order2 = XSN_BTC_SELL_LIMIT_1

        val expected = peers.ws.WebSocketOutgoingMessage(
          2,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected("Your order was matched with one of your own orders")
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))
        discardMsg(alice)

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order2, Some(xsnRHash)))
        alice.client.expectMsg(expected)
      }
    }

    "reject an order if the id already exists" in {
      pending
    }

    "reject a buy market order when funds are lower than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val orderId = OrderId.random()
        val order = XSN_BTC.Order.market(OrderSide.Buy, orderId, funds = XSN_BTC.buyFundsInterval.from - Satoshis.One)

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.buyFundsInterval.from}, ${XSN_BTC.buyFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a buy market order when funds are higher than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val orderId = OrderId.random()
        val order = XSN_BTC.Order.market(OrderSide.Buy, orderId, funds = XSN_BTC.buyFundsInterval.to + Satoshis.One)

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.buyFundsInterval.from}, ${XSN_BTC.buyFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a sell market order when funds are lower than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val orderId = OrderId.random()
        val order = XSN_BTC.Order.market(OrderSide.Sell, orderId, funds = XSN_BTC.sellFundsInterval.from - Satoshis.One)

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.sellFundsInterval.from}, ${XSN_BTC.sellFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a sell market order when funds are higher than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val orderId = OrderId.random()
        val order = XSN_BTC.Order.market(OrderSide.Sell, orderId, funds = XSN_BTC.sellFundsInterval.to + Satoshis.One)

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.sellFundsInterval.from}, ${XSN_BTC.sellFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a buy limit order when funds are lower than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = XSN_BTC.buyFundsInterval.from - Satoshis.One,
          price = XSN_BTC.buyPriceInterval.from
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.buyFundsInterval.from}, ${XSN_BTC.buyFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a buy limit order when funds are higher than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = XSN_BTC.buyFundsInterval.to + Satoshis.One,
          price = XSN_BTC.buyPriceInterval.from
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.buyFundsInterval.from}, ${XSN_BTC.buyFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a sell limit order when funds are lower than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order =
          XSN_BTC.Order.limit(
            OrderSide.Sell,
            OrderId.random(),
            funds = XSN_BTC.sellFundsInterval.from - Satoshis.One,
            price = XSN_BTC.sellPriceInterval.from
          )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.sellFundsInterval.from}, ${XSN_BTC.sellFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a sell limit order when funds are higher than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.to + Satoshis.One,
          price = XSN_BTC.sellPriceInterval.from
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.funds} funds provided are outside the accepted range [${XSN_BTC.sellFundsInterval.from}, ${XSN_BTC.sellFundsInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a buy limit order when price is lower than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = XSN_BTC.buyFundsInterval.from,
          price = XSN_BTC.buyPriceInterval.from - Satoshis.One
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.details.price} price provided is outside the accepted range [${XSN_BTC.buyPriceInterval.from}, ${XSN_BTC.buyPriceInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a buy limit order when price is higher than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = XSN_BTC.buyFundsInterval.from,
          price = XSN_BTC.buyPriceInterval.to + Satoshis.One
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.details.price} price provided is outside the accepted range [${XSN_BTC.buyPriceInterval.from}, ${XSN_BTC.buyPriceInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a sell limit order when price is lower than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order =
          XSN_BTC.Order.limit(
            OrderSide.Sell,
            OrderId.random(),
            funds = XSN_BTC.sellFundsInterval.from,
            price = XSN_BTC.sellPriceInterval.from - Satoshis.One
          )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.details.price} price provided is outside the accepted range [${XSN_BTC.sellPriceInterval.from}, ${XSN_BTC.sellPriceInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject a sell limit order when price is higher than the accepted interval" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to + Satoshis.One
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderRejected(
              s"The ${order.details.price} price provided is outside the accepted range [${XSN_BTC.sellPriceInterval.from}, ${XSN_BTC.sellPriceInterval.to}]"
            )
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "place order when fee is provided and fees feature flag is on" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesEnabled = true, feesRepository = feesRepository) { alice =>
        when(feesRepository.findInvoice(xsnRHash, Currency.XSN)).thenReturn(
          Some(FeeInvoice(xsnRHash, Currency.XSN, Satoshis.One, Instant.now()))
        )
        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(
          Some(Fee(Currency.XSN, xsnRHash, getSatoshis(100000), None, Instant.now(), BigDecimal(0.0025)))
        )

        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(order)
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "place order when fee invoice does no exist and fees feature flag is off" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesRepository = feesRepository) { alice =>
        when(feesRepository.findInvoice(xsnRHash, Currency.XSN)).thenReturn(None)
        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(
          Some(Fee(Currency.XSN, xsnRHash, getSatoshis(100000), None, Instant.now(), BigDecimal(0.0025)))
        )

        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(order)
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject order when fee invoice does no exist and fees feature flag is on" in {
      val feesRepository = mock[FeesRepository.Blocking]

      withSinglePeer(feesEnabled = true, feesRepository = feesRepository) { alice =>
        when(feesRepository.findInvoice(xsnRHash, Currency.XSN)).thenReturn(None)
        when(feesRepository.find(xsnRHash, Currency.XSN)).thenReturn(
          Some(Fee(Currency.XSN, xsnRHash, getSatoshis(100000), None, Instant.now(), BigDecimal(0.0025)))
        )

        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.CommandFailed(s"fee for $xsnRHash in ${Currency.XSN} was not found")
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "reject order when fee is missing and fees feature flag is on" in {
      withSinglePeer(feesEnabled = true) { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            peers.results.PlaceOrderResult.OrderRejected("A fee is required but no payment was provided")
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, None))

        alice.client.expectMsg(expected)
      }
    }

    "place order when fee is missing and fees feature flag is off" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(order)
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, None))

        alice.client.expectMsg(expected)
      }
    }

    "place order when fee is provided and fees feature flag is off" in {
      withSinglePeer() { alice =>
        val requestId = "id"
        val order = XSN_BTC.Order.limit(
          OrderSide.Sell,
          OrderId.random(),
          funds = XSN_BTC.sellFundsInterval.from,
          price = XSN_BTC.sellPriceInterval.to
        )

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some(requestId),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(order)
          )
        )

        alice.actor ! peers.ws
          .WebSocketIncomingMessage(requestId, peers.protocol.Command.PlaceOrder(order, Some(xsnRHash)))

        alice.client.expectMsg(expected)
      }
    }

    "burn fees when orders are matched and fees feature flag is on" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val makerPaymentService = mock[MakerPaymentService]
      val paymentService = mock[PaymentService]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withTwoPeers(
        feesEnabled = true,
        feesRepository = feesRepository,
        makerPaymentService = makerPaymentService,
        paymentService = paymentService,
        clientsRepository = clientsRepository
      ) { (alice, bob) =>
        // This is required to invoke the burn fee method, as it's invoked only if the fee actually exist
        val fee = Fee(Currency.XSN, xsnRHash2, getSatoshis(100000), None, Instant.now(), BigDecimal(0.0025))
        when(feesRepository.find(any[OrderId], any[Currency])).thenReturn(Some(fee))
        when(feesRepository.find(any[PaymentRHash], any[Currency])).thenReturn(Some(fee))
        when(feesRepository.findInvoice(xsnRHash, Currency.LTC)).thenReturn(
          Some(FeeInvoice(xsnRHash, Currency.XSN, Satoshis.One, Instant.now()))
        )
        when(feesRepository.findInvoice(xsnRHash2, Currency.XSN)).thenReturn(
          Some(FeeInvoice(xsnRHash2, Currency.LTC, Satoshis.One, Instant.now()))
        )

        when(makerPaymentService.payMaker(any[ClientId], any[PeerTrade])).thenReturn(Future.successful(Right(())))

        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob)
        discardMsg(alice)

        val messageProto = SwapCompletePacketBody(rHash = "45654654654")

        val swapPacket = Packet().withComplete(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )

        val bobFee = Satoshis.from(BigDecimal(0.01)).value
        val bobExpectedBurnFeeRequest = BurnFeeRequest(bobOrder.value.id, Currency.XSN, bobFee)
        verify(feesRepository, Mockito.timeout(1000)).burn(bobExpectedBurnFeeRequest)

        val aliceFee = Satoshis.from(BigDecimal(1)).value
        val aliceExpectedBurnFeeRequest = BurnFeeRequest(aliceOrder.value.id, Currency.LTC, aliceFee)
        verify(feesRepository, Mockito.timeout(1000)).burn(aliceExpectedBurnFeeRequest)
      }
    }

    "not burn fees when orders are matched and fees feature flag is off" in {
      val feesRepositoryMock = mock[FeesRepository.Blocking]
      withTwoPeers(feesEnabled = false, feesRepository = feesRepositoryMock) { (alice, bob) =>
        when(feesRepositoryMock.find(any[OrderId], any[Currency])).thenReturn(None)
        when(feesRepositoryMock.find(any[PaymentRHash], any[Currency])).thenReturn(None)

        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob)
        discardMsg(alice)

        val messageProto = SwapCompletePacketBody(rHash = "45654654654")

        val swapPacket = Packet().withComplete(messageProto).toByteArray.toVector

        // alice sends a message to bob
        alice.actor ! peers.ws.WebSocketIncomingMessage(
          "id",
          peers.protocol.Command.SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id))
        )

        verify(
          feesRepositoryMock,
          Mockito
            .after(500)
            .times(0)
        ).burn(any[BurnFeeRequest])
      }
    }

    "not match against a disconnected peer orders" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        watch(alice.actor)
        alice.actor ! PoisonPill
        expectTerminated(alice.actor)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some("id"),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(bobOrder)
          )
        )

        bob.client.expectMsg(expected)
      }
    }

    "not match against a cancelled order" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.CancelOpenOrder(aliceOrder.value.id))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some("id"),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(bobOrder)
          )
        )

        bob.client.expectMsg(expected)
      }
    }

    "not match against orders removed by the owner" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        alice.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.CleanTradingPairOrders(aliceOrder.pair))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! peers.ws
          .WebSocketIncomingMessage("id", peers.protocol.Command.PlaceOrder(bobOrder, Some(xsnRHash2)))

        val expected = peers.ws.WebSocketOutgoingMessage(
          1,
          Some("id"),
          peers.protocol.Event.CommandResponse.PlaceOrderResponse(
            PlaceOrderResult.OrderPlaced(bobOrder)
          )
        )

        bob.client.expectMsg(expected)
      }
    }

    "cancel a trade when swap success/failure its not reported within the configured waiting time" in {
      withTwoPeers() { (alice, bob) =>
        val aliceOrder = XSN_LTC_SELL_LIMIT_1
        val bobOrder = XSN_LTC_BUY_LIMIT_1

        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, None))
        discardMsg(alice)

        bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, None))
        discardMsg(bob)
        val trade = alice.client.expectMsgPF() {
          case peers.ws.WebSocketOutgoingMessage(_, _, e: peers.protocol.Event.ServerEvent.MyOrderMatched) => e.trade
        }

        alice.client.expectMsg(WebSocketOutgoingMessage(3, None, MyMatchedOrderCanceled(trade)))
        bob.client.expectMsg(WebSocketOutgoingMessage(2, None, MyMatchedOrderCanceled(trade)))
      }
    }

    "pay maker" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val makerPaymentService = mock[MakerPaymentService]
      val paymentService = mock[PaymentService]
      val clientsRepository = mock[ClientsRepository.Blocking]

      withTwoPeers(
        feesRepository = feesRepository,
        makerPaymentService = makerPaymentService,
        paymentService = paymentService,
        clientsRepository = clientsRepository
      ) { (alice, bob) =>
        when(makerPaymentService.payMaker(any[ClientId], any[PeerTrade])).thenReturn(Future.successful(Right(())))

        val aliceOrder = XSN_LTC_BUY_LIMIT_1
        alice.actor ! WebSocketIncomingMessage("id", PlaceOrder(aliceOrder, Some(xsnRHash)))
        discardMsg(alice)

        val bobOrder = XSN_LTC_SELL_LIMIT_1
        bob.actor ! WebSocketIncomingMessage("id", PlaceOrder(bobOrder, Some(xsnRHash2)))
        discardMsg(bob)
        discardMsg(alice)

        val messageProto = SwapCompletePacketBody(rHash = "45654654654")
        val swapPacket = Packet().withComplete(messageProto).toByteArray.toVector
        alice.actor ! WebSocketIncomingMessage("id", SendOrderMessage(OrderMessage(swapPacket, aliceOrder.value.id)))

        verify(makerPaymentService, Mockito.timeout(1000)).payMaker(any[ClientId], any[PeerTrade])
      }
    }
  }
}
