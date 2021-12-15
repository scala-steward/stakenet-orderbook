package io.stakenet.orderbook.actors.peers

import helpers.Helpers
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GetConnextPaymentInformation
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.GetConnextPaymentInformationResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.services.PaymentService
import org.mockito.MockitoSugar._

import scala.concurrent.Future

class GetConnextPaymentInformationSpec extends PeerSpecBase("GetConnextPaymentInformationSpec") {
  "GetConnextPaymentInformation" should {
    s"generate the payment information when fees are on" in {
      val paymentService = mock[PaymentService]
      val connextHelper = mock[ConnextHelper]

      withSinglePeer(feesEnabled = true, paymentService = paymentService, connextHelper = connextHelper) { alice =>
        val paymentHash = Helpers.randomPaymentHash()
        val publicIdentifier = "vector8ZaxNSdUM83kLXJSsmj5jrcq17CpZUwBirmboaNPtQMEXjVNrL"
        val currency = Currency.WETH

        when(paymentService.generatePaymentHash(currency)).thenReturn(
          Future.successful(paymentHash)
        )

        when(connextHelper.getPublicIdentifier(currency)).thenReturn(publicIdentifier)

        alice.actor ! WebSocketIncomingMessage("id", GetConnextPaymentInformation(currency))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(
            1,
            Some("id"),
            GetConnextPaymentInformationResponse(currency, noFeeRequired = false, publicIdentifier, Some(paymentHash))
          )
        )
      }
    }

    s"not return a payment hash when fees are off" in {
      val connextHelper = mock[ConnextHelper]

      withSinglePeer(feesEnabled = false, connextHelper = connextHelper) { alice =>
        val publicIdentifier = "vector8ZaxNSdUM83kLXJSsmj5jrcq17CpZUwBirmboaNPtQMEXjVNrL"
        val currency = Currency.WETH

        when(connextHelper.getPublicIdentifier(currency)).thenReturn(publicIdentifier)

        alice.actor ! WebSocketIncomingMessage("id", GetConnextPaymentInformation(currency))

        alice.client.expectMsg(
          WebSocketOutgoingMessage(
            1,
            Some("id"),
            GetConnextPaymentInformationResponse(currency, noFeeRequired = true, publicIdentifier, None)
          )
        )
      }
    }
  }
}
