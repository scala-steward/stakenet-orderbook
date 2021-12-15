package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Command.GetInvoicePayment
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{CommandFailed, GetInvoicePaymentResponse}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.services.PaymentService
import org.mockito.MockitoSugar._

import scala.concurrent.Future

class GetInvoicePaymentSpec extends PeerSpecBase("GetInvoicePaymentSpec") {
  "GetInvoicePayment" should {
    Currency.forLnd.foreach { currency =>
      s"generate the invoice for $currency" in {
        val paymentService = mock[PaymentService]
        val feesRepository = mock[FeesRepository.Blocking]

        withSinglePeer(feesEnabled = true, paymentService = paymentService, feesRepository = feesRepository) { alice =>
          val invoice = "invoice"

          when(paymentService.createPaymentRequest(currency, Satoshis.One, "Fee for placing order")).thenReturn(
            Future.successful(Right(invoice))
          )

          when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(xsnRHash))
          when(feesRepository.createInvoice(xsnRHash, currency, Satoshis.One)).thenReturn(())

          alice.actor ! WebSocketIncomingMessage("id", GetInvoicePayment(currency, Satoshis.One))

          alice.client.expectMsg(
            WebSocketOutgoingMessage(
              1,
              Some("id"),
              GetInvoicePaymentResponse(currency, paymentRequest = Some(invoice), noFeeRequired = false)
            )
          )
        }
      }

      s"return noFeeRequired = true when fees are disabled for $currency" in {
        withSinglePeer(feesEnabled = false) { alice =>
          alice.actor ! WebSocketIncomingMessage("id", GetInvoicePayment(currency, Satoshis.One))

          alice.client.expectMsg(
            WebSocketOutgoingMessage(
              1,
              Some("id"),
              GetInvoicePaymentResponse(currency, paymentRequest = None, noFeeRequired = true)
            )
          )
        }
      }

      s"fail when invoice could not be created for $currency" in {
        val paymentService = mock[PaymentService]
        val feesRepository = mock[FeesRepository.Blocking]

        withSinglePeer(feesEnabled = true, paymentService = paymentService, feesRepository = feesRepository) { alice =>
          val invoice = "invoice"

          when(paymentService.createPaymentRequest(currency, Satoshis.One, "Fee for placing order")).thenReturn(
            Future.failed(new RuntimeException("error"))
          )

          when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(xsnRHash))
          when(feesRepository.createInvoice(xsnRHash, currency, Satoshis.One)).thenReturn(())

          alice.actor ! WebSocketIncomingMessage("id", GetInvoicePayment(currency, Satoshis.One))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed("error")))
        }
      }

      s"fail when payment hash for invoice could not be obtained for $currency" in {
        val paymentService = mock[PaymentService]
        val feesRepository = mock[FeesRepository.Blocking]

        withSinglePeer(feesEnabled = true, paymentService = paymentService, feesRepository = feesRepository) { alice =>
          val invoice = "invoice"

          when(paymentService.createPaymentRequest(currency, Satoshis.One, "Fee for placing order")).thenReturn(
            Future.successful(Right(invoice))
          )

          when(paymentService.getPaymentHash(currency, invoice)).thenReturn(
            Future.failed(new RuntimeException("error"))
          )

          when(feesRepository.createInvoice(xsnRHash, currency, Satoshis.One)).thenReturn(())

          alice.actor ! WebSocketIncomingMessage("id", GetInvoicePayment(currency, Satoshis.One))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed("error")))
        }
      }

      s"fail when invoice could not be stored in the database for $currency" in {
        val paymentService = mock[PaymentService]
        val feesRepository = mock[FeesRepository.Blocking]

        withSinglePeer(feesEnabled = true, paymentService = paymentService, feesRepository = feesRepository) { alice =>
          val invoice = "invoice"

          when(paymentService.createPaymentRequest(currency, Satoshis.One, "Fee for placing order")).thenReturn(
            Future.successful(Right(invoice))
          )

          when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(xsnRHash))
          when(feesRepository.createInvoice(xsnRHash, currency, Satoshis.One)).thenThrow(new RuntimeException("error"))

          alice.actor ! WebSocketIncomingMessage("id", GetInvoicePayment(currency, Satoshis.One))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed("error")))
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      s"fail for $currency" in {
        withSinglePeer(feesEnabled = true) { alice =>
          alice.actor ! WebSocketIncomingMessage("id", GetInvoicePayment(currency, Satoshis.One))

          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some("id"), CommandFailed(s"$currency not supported")))
        }
      }
    }
  }
}
