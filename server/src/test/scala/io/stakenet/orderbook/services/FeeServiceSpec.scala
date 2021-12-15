package io.stakenet.orderbook.services

import helpers.Helpers
import helpers.Helpers.randomPaymentHash
import io.stakenet.orderbook.config.OrderFeesConfig
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.connext.ConnextHelper.ResolveTransferError.NoChannelWithCounterParty
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.helpers.SampleOrders.{getSatoshis, toTradingOrder}
import io.stakenet.orderbook.helpers.{Executors, SampleOrders}
import io.stakenet.orderbook.models.clients.ClientIdentifier.ClientConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, ClientPublicIdentifierId}
import io.stakenet.orderbook.models.lnd.RefundStatus.{Failed, Processing, Refunded}
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.trading.TradingPair.{BTC_WETH, XSN_BTC}
import io.stakenet.orderbook.models.trading.{OrderSide, TradingOrder}
import io.stakenet.orderbook.models.{Currency, OrderId, Preimage, Satoshis}
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.repositories.fees.requests.{BurnFeeRequest, LinkFeeToOrderRequest}
import io.stakenet.orderbook.repositories.preimages.PreimagesRepository
import io.stakenet.orderbook.repositories.reports
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.services.FeeService.Errors.{CouldNotRefundFee, CouldNotTakeFee}
import io.stakenet.orderbook.services.PaymentService.Error.PaymentFailed
import io.stakenet.orderbook.services.impl.LndFeeService
import io.stakenet.orderbook.services.validators.Fees
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class FeeServiceSpec extends AsyncWordSpec with Matchers {
  "takeFee" should {
    "succeed when receiving an existing fee" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      val fee =
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, Instant.now, 0.025)
      val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.paidAmount, Instant.now)
      when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee))
      when(feesRepository.findInvoice(fee.paymentRHash, fee.currency)).thenReturn(Some(invoice))

      service.takeFee(clientId, order, fee.paymentRHash).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).linkOrder(any[LinkFeeToOrderRequest])

        result mustBe Right(())
      }
    }

    "fail on existing fee does not have enough funds" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val fee = Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.0001"), None, Instant.now, 0.025)
      val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.paidAmount, Instant.now)
      when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee))
      when(feesRepository.findInvoice(fee.paymentRHash, fee.currency)).thenReturn(Some(invoice))

      val error = "The max funds you can place for this payment hash is: 0.00010000 XSN, but 1.00000000 XSN received."
      service.takeFee(clientId, SampleOrders.XSN_LTC_SELL_LIMIT_1, fee.paymentRHash).map { result =>
        result mustBe Left(CouldNotTakeFee(error))
      }
    }

    "fail on existing fee is already locked for another order" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val orderId = OrderId.random()
      val paymentHash = randomPaymentHash()
      val fee =
        Fee(Currency.XSN, paymentHash, Helpers.asSatoshis("1.0"), Some(orderId), Instant.now, 0.02)
      val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.paidAmount, Instant.now)
      when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee))
      when(feesRepository.findInvoice(fee.paymentRHash, fee.currency)).thenReturn(Some(invoice))

      val error = s"The fee $paymentHash is already locked on the order $orderId"
      service.takeFee(clientId, SampleOrders.XSN_LTC_SELL_LIMIT_1, fee.paymentRHash).map { result =>
        result mustBe Left(CouldNotTakeFee(error))
      }
    }

    "fail when invoice does not exist" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      val paymentHash = randomPaymentHash()
      val currency = Currency.XSN
      when(feesRepository.findInvoice(paymentHash, currency)).thenReturn(None)

      service.takeFee(clientId, order, paymentHash).map { result =>
        result mustBe Left(CouldNotTakeFee(s"fee for $paymentHash in $currency was not found"))
      }
    }

    "succeed when receiving a new fee" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val service = getService(feesRepository = feesRepository, paymentService = paymentService)

      val paymentHash = randomPaymentHash()
      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      val invoice = FeeInvoice(paymentHash, Currency.XSN, Satoshis.One, Instant.now)
      when(feesRepository.find(paymentHash, Currency.XSN)).thenReturn(None)
      when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
      when(paymentService.validatePayment(Fees.getCurrencyPayment(order), paymentHash)).thenReturn(
        Future.successful(PaymentData(Helpers.asSatoshis("1.0"), Instant.now()))
      )

      service.takeFee(clientId, order, paymentHash).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).linkOrder(any[LinkFeeToOrderRequest])

        result mustBe Right(())
      }
    }

    "succeed when receiving an order with rounding problems" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val service = getService(feesRepository = feesRepository, paymentService = paymentService)

      val paymentHash = randomPaymentHash()
      val pair = XSN_BTC
      val order = TradingOrder(pair)(
        pair.Order.limit(
          OrderSide.Buy,
          OrderId.random(),
          funds = Helpers.asSatoshis("0.001413"),
          price = Helpers.asSatoshis("0.00000471")
        )
      )
      val invoice = FeeInvoice(paymentHash, Currency.BTC, Satoshis.One, Instant.now)
      when(feesRepository.find(paymentHash, Currency.BTC)).thenReturn(None)
      when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
      when(paymentService.validatePayment(Fees.getCurrencyPayment(order), paymentHash)).thenReturn(
        Future.successful(PaymentData(Helpers.asSatoshis("0.00000353"), Instant.now()))
      )

      service.takeFee(clientId, order, paymentHash).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).linkOrder(any[LinkFeeToOrderRequest])

        result mustBe Right(())
      }
    }

    "fail when a new fee does not have enough funds" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val service = getService(feesRepository = feesRepository, paymentService = paymentService)

      val paymentHash = randomPaymentHash()
      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      val paidAmount = Helpers.asSatoshis("0.0000001")
      val paidCurrency = Currency.XSN
      val invoice = FeeInvoice(paymentHash, paidCurrency, paidAmount, Instant.now)
      when(feesRepository.find(paymentHash, paidCurrency)).thenReturn(None)
      when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
      when(paymentService.validatePayment(Fees.getCurrencyPayment(order), paymentHash)).thenReturn(
        Future.successful(PaymentData(paidAmount, Instant.now()))
      )

      val error =
        s"The expected fee to place the order is 0.00010000 XSN, but ${paidAmount.toString(paidCurrency)} received."
      service.takeFee(clientId, order, paymentHash).map { result =>
        result mustBe Left(CouldNotTakeFee(error))
      }
    }

    "take a connext fee" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val connextHelper = mock[ConnextHelper]
      val clientService = mock[ClientService]
      val preimagesRepository = mock[PreimagesRepository.Blocking]
      val service = getService(
        feesRepository = feesRepository,
        connextHelper = connextHelper,
        clientService = clientService,
        preimagesRepository = preimagesRepository
      )

      val order = SampleOrders.buyMarketOrder(BTC_WETH, funds = getSatoshis(123456))
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val publicIdentifier = Helpers.randomPublicIdentifier()
      when(feesRepository.find(paymentHash, Currency.WETH)).thenReturn(None)
      when(clientService.findPublicIdentifier(clientId, Currency.WETH)).thenReturn(
        Future.successful(
          Some(
            ClientConnextPublicIdentifier(ClientPublicIdentifierId.random(), publicIdentifier, Currency.WETH, clientId)
          )
        )
      )
      when(preimagesRepository.findPreimage(paymentHash, Currency.WETH)).thenReturn(Some(preimage))
      when(connextHelper.resolveTransfer(Currency.WETH, publicIdentifier, paymentHash, preimage)).thenReturn(
        Future.successful(Right(PaymentData(getSatoshis(123), Instant.now)))
      )

      service.takeFee(clientId, order, paymentHash).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).linkOrder(any[LinkFeeToOrderRequest])

        result mustBe Right(())
      }
    }

    "fail when transfer fails to resolve" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val connextHelper = mock[ConnextHelper]
      val clientService = mock[ClientService]
      val preimagesRepository = mock[PreimagesRepository.Blocking]
      val service = getService(
        feesRepository = feesRepository,
        connextHelper = connextHelper,
        clientService = clientService,
        preimagesRepository = preimagesRepository
      )

      val order = SampleOrders.buyMarketOrder(BTC_WETH, funds = getSatoshis(123456))
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val publicIdentifier = Helpers.randomPublicIdentifier()
      when(feesRepository.find(paymentHash, Currency.WETH)).thenReturn(None)
      when(clientService.findPublicIdentifier(clientId, Currency.WETH)).thenReturn(
        Future.successful(
          Some(
            ClientConnextPublicIdentifier(ClientPublicIdentifierId.random(), publicIdentifier, Currency.WETH, clientId)
          )
        )
      )
      when(preimagesRepository.findPreimage(paymentHash, Currency.WETH)).thenReturn(Some(preimage))
      when(connextHelper.resolveTransfer(Currency.WETH, publicIdentifier, paymentHash, preimage)).thenReturn(
        Future.successful(Left(NoChannelWithCounterParty(publicIdentifier)))
      )

      service.takeFee(clientId, order, paymentHash).map { result =>
        result mustBe Left(CouldNotTakeFee(s"no channel with client $clientId"))
      }
    }

    "fail when preimage is not found" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val connextHelper = mock[ConnextHelper]
      val clientService = mock[ClientService]
      val preimagesRepository = mock[PreimagesRepository.Blocking]
      val service = getService(
        feesRepository = feesRepository,
        connextHelper = connextHelper,
        clientService = clientService,
        preimagesRepository = preimagesRepository
      )

      val order = SampleOrders.buyMarketOrder(BTC_WETH, funds = getSatoshis(123456))
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val publicIdentifier = Helpers.randomPublicIdentifier()
      when(feesRepository.find(paymentHash, Currency.WETH)).thenReturn(None)
      when(clientService.findPublicIdentifier(clientId, Currency.WETH)).thenReturn(
        Future.successful(
          Some(
            ClientConnextPublicIdentifier(ClientPublicIdentifierId.random(), publicIdentifier, Currency.WETH, clientId)
          )
        )
      )
      when(preimagesRepository.findPreimage(paymentHash, Currency.WETH)).thenReturn(None)

      service.takeFee(clientId, order, paymentHash).map { result =>
        result mustBe Left(CouldNotTakeFee(s"preimage for $paymentHash in WETH not found"))
      }
    }

    "fail when client has no public identifier" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val connextHelper = mock[ConnextHelper]
      val clientService = mock[ClientService]
      val preimagesRepository = mock[PreimagesRepository.Blocking]
      val service = getService(
        feesRepository = feesRepository,
        connextHelper = connextHelper,
        clientService = clientService,
        preimagesRepository = preimagesRepository
      )

      val order = SampleOrders.buyMarketOrder(BTC_WETH, funds = getSatoshis(123456))
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      when(feesRepository.find(paymentHash, Currency.WETH)).thenReturn(None)
      when(clientService.findPublicIdentifier(clientId, Currency.WETH)).thenReturn(Future.successful(None))

      service.takeFee(clientId, order, paymentHash).map { result =>
        result mustBe Left(CouldNotTakeFee(s"Client $clientId has not public identifier for WETH"))
      }
    }

    "work when expected fee has more tha 8 digits" in {
      val clientId = ClientId.random()
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]
      val service = getService(feesRepository = feesRepository, paymentService = paymentService)

      val paymentHash = randomPaymentHash()
      val order = XSN_BTC.Order.limit(
        side = OrderSide.Sell,
        id = OrderId.random(),
        funds = Satoshis.from(BigDecimal("0.00011")).value,
        price = Satoshis.from(BigDecimal("0.000011")).value
      )
      val invoice = FeeInvoice(paymentHash, Currency.XSN, Satoshis.One, Instant.now)
      when(feesRepository.find(paymentHash, Currency.XSN)).thenReturn(None)
      when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
      when(paymentService.validatePayment(Fees.getCurrencyPayment(order), paymentHash)).thenReturn(
        Future.successful(PaymentData(Satoshis.from(BigDecimal("0.00000001")).value, Instant.now()))
      )

      service.takeFee(clientId, order, paymentHash).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).linkOrder(any[LinkFeeToOrderRequest])

        result mustBe Right(())
      }
    }
  }

  "unlink" should {
    "unlink order from fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      service.unlink(order.value.id).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).unlink(order.value.id)

        result mustBe (())
      }
    }
  }

  "burn" should {
    "burn fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      service.burn(order.value.id, Currency.XSN, Satoshis.One).map { result =>
        verify(feesRepository, Mockito.timeout(1000)).burn(
          BurnFeeRequest(order.value.id, Currency.XSN, Satoshis.One)
        )

        result mustBe (())
      }
    }
  }

  "find" should {
    "return the fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      val fee =
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, Instant.now, 0.025)
      when(feesRepository.find(order.value.id, fee.currency)).thenReturn(Some(fee))

      service.find(order.value.id, fee.currency).map { result =>
        result mustBe Some(fee)
      }
    }

    "return None when fee does not exists" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val service = getService(feesRepository = feesRepository)

      val order = SampleOrders.XSN_LTC_SELL_LIMIT_1
      when(feesRepository.find(order.value.id, Currency.XSN)).thenReturn(None)

      service.find(order.value.id, Currency.XSN).map { result =>
        result mustBe None
      }
    }
  }

  "refund" should {
    "refund fees" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val clientIdentifier = Helpers.randomClientPublicKey()

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, refundedAmount, Currency.XSN)).thenReturn(
        Future.successful(Right(()))
      )

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(clientIdentifier.clientId, Currency.XSN, refundedFees).map {
        case Right((amount, _)) => amount mustBe refundedAmount
        case _ => fail()
      }
    }

    "fail when payment fails" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val clientIdentifier = Helpers.randomClientPublicKey()

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, refundedAmount, Currency.XSN)).thenReturn(
        Future.successful(Left(PaymentFailed("payment failed")))
      )

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(clientIdentifier.clientId, Currency.XSN, refundedFees).map { result =>
        result mustBe Left(CouldNotRefundFee("payment failed"))
      }
    }

    "fail when payment fails for an unexpected reason" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val clientIdentifier = Helpers.randomClientPublicKey()

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, refundedAmount, Currency.XSN)).thenReturn(
        Future.failed(new RuntimeException("Connection failed"))
      )

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(clientIdentifier.clientId, Currency.XSN, refundedFees).map { result =>
        result mustBe Left(CouldNotRefundFee("Connection failed"))
      }
    }

    "fail when one of the fees is not found" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )

      when(feesRepository.find(fees.head.paymentRHash, fees.head.currency)).thenReturn(None)
      when(feesRepository.findInvoice(fees.head.paymentRHash, fees.head.currency)).thenReturn(None)
      when(paymentService.isPaymentComplete(fees.head.currency, fees.head.paymentRHash)).thenReturn(
        Future.successful(false)
      )
      when(feesRepository.find(fees(1).paymentRHash, fees(1).currency)).thenReturn(Some(fees(1)))
      when(feesRepository.find(fees(2).paymentRHash, fees(2).currency)).thenReturn(Some(fees(2)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        result mustBe Left(CouldNotRefundFee(s"[Fee with payment hash ${fees.head.paymentRHash} not found]"))
      }
    }

    "fail when one of the fees is locked for an order" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val orderId = OrderId.random()
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), Some(orderId), twentyYearsAgo, 0)
      )

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        val error = s"[Fee ${fees(2).paymentRHash} is locked for order $orderId]"
        result mustBe Left(CouldNotRefundFee(error))
      }
    }

    "fail when fee has not spent enough time on the hub" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, Instant.now, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        val error = s"[Fee ${fees.head.paymentRHash} needs to wait 1 second from the fee payment for a refund]"

        result mustBe Left(CouldNotRefundFee(error))
      }
    }

    "fail when a payment hash is sent multiple times" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val paymentHash = randomPaymentHash()
      val fees = List(
        Fee(Currency.XSN, paymentHash, Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, paymentHash, Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        val error = s"[$paymentHash was sent 2 times]"

        result mustBe Left(CouldNotRefundFee(error))
      }
    }

    "return multiple errors" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val orderId = OrderId.random()
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(
          Currency.XSN,
          randomPaymentHash(),
          Helpers.asSatoshis("0.00123456"),
          Some(orderId),
          twentyYearsAgo,
          0
        ),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, Instant.now, 0.025)
      )

      when(feesRepository.find(fees.head.paymentRHash, fees.head.currency)).thenReturn(None)
      when(feesRepository.findInvoice(fees.head.paymentRHash, fees.head.currency)).thenReturn(None)
      when(paymentService.isPaymentComplete(fees.head.currency, fees.head.paymentRHash)).thenReturn(
        Future.successful(false)
      )
      when(feesRepository.find(fees(1).paymentRHash, fees(1).currency)).thenReturn(Some(fees(1)))
      when(feesRepository.find(fees(2).paymentRHash, fees(2).currency)).thenReturn(Some(fees(2)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        val error1 = s"Fee with payment hash ${fees.head.paymentRHash} not found"
        val error2 = s"Fee ${fees(1).paymentRHash} is locked for order $orderId"
        val error3 = s"Fee ${fees(2).paymentRHash} needs to wait 1 second from the fee payment for a refund"

        result mustBe Left(CouldNotRefundFee(s"[$error1, $error2, $error3]"))
      }
    }

    "fail when creating refund fails for unexpected reason" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val hashes = refundedFees.map(_.paymentRHash)
      val clientIdentifier = Helpers.randomClientPublicKey()

      fees.foreach(fee => when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(Some(fee)))
      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(None)
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, refundedAmount, Currency.XSN)).thenReturn(
        Future.successful(Right(()))
      )
      when(feeRefundsRepository.createRefund(hashes, Currency.XSN, refundedAmount)).thenThrow(
        new RuntimeException("Timeout")
      )

      service.refund(clientIdentifier.clientId, Currency.XSN, refundedFees).map { result =>
        result mustBe Left(CouldNotRefundFee("Timeout"))
      }
    }

    "fail refund is processing" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val refund = FeeRefund(FeeRefund.Id.random(), Currency.XSN, refundedAmount, Processing, Instant.now, None)

      fees.foreach(f => when(feeRefundsRepository.find(f.paymentRHash, Currency.XSN)).thenReturn(Some(refund)))

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        result mustBe Left(CouldNotRefundFee("Refund is already in process"))
      }
    }

    "return refund data when refund was already done" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val refund = FeeRefund(
        FeeRefund.Id.random(),
        Currency.XSN,
        refundedAmount,
        Refunded,
        Instant.now,
        Some(Instant.now)
      )

      fees.foreach(f => when(feeRefundsRepository.find(f.paymentRHash, Currency.XSN)).thenReturn(Some(refund)))

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(ClientId.random(), Currency.XSN, refundedFees).map { result =>
        result mustBe Right((refund.amount, refund.refundedOn.value))
      }
    }

    "retry refund when previous refund failed" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val refund = FeeRefund(FeeRefund.Id.random(), Currency.XSN, refundedAmount, Failed, Instant.now, None)
      val clientIdentifier = Helpers.randomClientPublicKey()

      fees.foreach(f => when(feeRefundsRepository.find(f.paymentRHash, Currency.XSN)).thenReturn(Some(refund)))
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, refundedAmount, Currency.XSN)).thenReturn(
        Future.successful(Right(()))
      )

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(clientIdentifier.clientId, Currency.XSN, refundedFees).map {
        case Right((amount, _)) => amount mustBe refundedAmount
        case _ => fail()
      }
    }

    "fail when some fees are already part of different refunds" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fees = List(
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("1.0"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025),
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00456789"), None, twentyYearsAgo, 0.025)
      )
      val refundedAmount = fees.map(_.refundableFeeAmount).foldLeft(Satoshis.Zero)(_ + _)
      val refund1 = FeeRefund(FeeRefund.Id.random(), Currency.XSN, refundedAmount, Failed, Instant.now, None)
      val refund2 = FeeRefund(FeeRefund.Id.random(), Currency.XSN, refundedAmount, Failed, Instant.now, None)
      val clientIdentifier = Helpers.randomClientPublicKey()

      when(feeRefundsRepository.find(fees.head.paymentRHash, Currency.XSN)).thenReturn(Some(refund1))
      when(feeRefundsRepository.find(fees(1).paymentRHash, Currency.XSN)).thenReturn(Some(refund2))
      when(feeRefundsRepository.find(fees(2).paymentRHash, Currency.XSN)).thenReturn(None)
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, refundedAmount, Currency.XSN)).thenReturn(
        Future.successful(Right(()))
      )

      val refundedFees = fees.map(fee => RefundablePayment(fee.paymentRHash, fee.paidAmount))
      service.refund(clientIdentifier.clientId, Currency.XSN, refundedFees).map { result =>
        result mustBe Left(CouldNotRefundFee("Invalid request"))
      }
    }

    "refund unused fee" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]
      val clientService = mock[ClientService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService,
        clientService = clientService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fee = Fee(
        Currency.XSN,
        randomPaymentHash(),
        Helpers.asSatoshis("0.00123456"),
        None,
        twentyYearsAgo,
        0.025
      )
      val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.refundableFeeAmount, Instant.now)
      val clientIdentifier = Helpers.randomClientPublicKey()

      when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(None)
      when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
      when(paymentService.isPaymentComplete(fee.currency, fee.paymentRHash)).thenReturn(Future.successful(true))
      when(feeRefundsRepository.find(fee.paymentRHash, Currency.XSN)).thenReturn(None)
      when(clientService.findPublicKey(clientIdentifier.clientId, Currency.XSN)).thenReturn(
        Future.successful(Some(clientIdentifier))
      )
      when(paymentService.keySend(clientIdentifier.key, fee.refundableFeeAmount, Currency.XSN)).thenReturn(
        Future.successful(Right(()))
      )

      val refund = RefundablePayment(fee.paymentRHash, fee.paidAmount)
      service.refund(clientIdentifier.clientId, Currency.XSN, List(refund)).map {
        case Right((amount, _)) => amount mustBe fee.refundableFeeAmount
        case _ => fail()
      }
    }

    "fail to refund unused fee when invoice is not paid" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val feeRefundsRepository = mock[FeeRefundsRepository.Blocking]
      val paymentService = mock[PaymentService]

      val service = getService(
        feesRepository = feesRepository,
        feeRefundsRepository = feeRefundsRepository,
        paymentService = paymentService
      )

      val twentyYearsAgo = Instant.ofEpochSecond(956021903)
      val fee =
        Fee(Currency.XSN, randomPaymentHash(), Helpers.asSatoshis("0.00123456"), None, twentyYearsAgo, 0.025)
      val invoice = FeeInvoice(fee.paymentRHash, fee.currency, fee.refundableFeeAmount, Instant.now)

      when(feesRepository.find(fee.paymentRHash, fee.currency)).thenReturn(None)
      when(feesRepository.findInvoice(invoice.paymentHash, invoice.currency)).thenReturn(Some(invoice))
      when(paymentService.isPaymentComplete(fee.currency, fee.paymentRHash)).thenReturn(Future.successful(false))
      when(feeRefundsRepository.find(fee.paymentRHash, Currency.XSN)).thenReturn(None)

      val refund = RefundablePayment(fee.paymentRHash, fee.paidAmount)
      service.refund(ClientId.random(), Currency.XSN, List(refund)).map { result =>
        result mustBe Left(CouldNotRefundFee(s"[Fee with payment hash ${fee.paymentRHash} is not paid]"))
      }
    }
  }

  "createInvoice" should {
    "create the invoice" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      val feeService = getService(
        feesRepository = feesRepository,
        paymentService = paymentService
      )

      val currency = Currency.XSN
      val amount = Satoshis.One
      val memo = "Fee for placing order"

      val invoice = "invoice"
      val paymentHash = randomPaymentHash()
      when(paymentService.createPaymentRequest(currency, amount, memo)).thenReturn(Future.successful(Right(invoice)))
      when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(paymentHash))
      when(feesRepository.createInvoice(paymentHash, currency, amount)).thenReturn(())

      feeService.createInvoice(currency, amount).map { result =>
        result mustBe Right(invoice)
      }
    }

    "fail when invoice creation fails" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      val feeService = getService(
        feesRepository = feesRepository,
        paymentService = paymentService
      )

      val currency = Currency.XSN
      val amount = Satoshis.One
      val memo = "Fee for placing order"

      val error = new RuntimeException("error")
      when(paymentService.createPaymentRequest(currency, amount, memo)).thenReturn(Future.failed(error))

      feeService.createInvoice(currency, amount).transform {
        case Failure(e: Exception) => Success(e.getMessage mustBe error.getMessage)
        case _ => fail()
      }
    }

    "fails when payment hash cant be obtained" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      val feeService = getService(
        feesRepository = feesRepository,
        paymentService = paymentService
      )

      val currency = Currency.XSN
      val amount = Satoshis.One
      val memo = "Fee for placing order"

      val invoice = "invoice"
      val error = new RuntimeException("error")
      when(paymentService.createPaymentRequest(currency, amount, memo)).thenReturn(Future.successful(Right(invoice)))
      when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.failed(error))

      feeService.createInvoice(currency, amount).transform {
        case Failure(e: Exception) => Success(e.getMessage mustBe error.getMessage)
        case _ => fail()
      }
    }

    "fail when storing the invoice fails" in {
      val feesRepository = mock[FeesRepository.Blocking]
      val paymentService = mock[PaymentService]

      val feeService = getService(
        feesRepository = feesRepository,
        paymentService = paymentService
      )

      val currency = Currency.XSN
      val amount = Satoshis.One
      val memo = "Fee for placing order"

      val invoice = "invoice"
      val paymentHash = randomPaymentHash()
      val error = new RuntimeException("error")
      when(paymentService.createPaymentRequest(currency, amount, memo)).thenReturn(Future.successful(Right(invoice)))
      when(paymentService.getPaymentHash(currency, invoice)).thenReturn(Future.successful(paymentHash))
      when(feesRepository.createInvoice(paymentHash, currency, amount)).thenThrow(error)

      feeService.createInvoice(currency, amount).transform {
        case Failure(e: Exception) => Success(e.getMessage mustBe error.getMessage)
        case _ => fail()
      }
    }
  }

  private def getService(
      feesRepository: FeesRepository.Blocking,
      feeRefundsRepository: FeeRefundsRepository.Blocking = mock[FeeRefundsRepository.Blocking],
      config: OrderFeesConfig = OrderFeesConfig(1.second),
      paymentService: PaymentService = mock[PaymentService],
      discordHelper: DiscordHelper = mock[DiscordHelper],
      reportsRepository: ReportsRepository.Blocking = mock[ReportsRepository.Blocking],
      connextHelper: ConnextHelper = mock[ConnextHelper],
      clientService: ClientService = mock[ClientService],
      preimagesRepository: PreimagesRepository.Blocking = mock[PreimagesRepository.Blocking]
  ) = {
    new LndFeeService(
      new FeesRepository.FutureImpl(feesRepository)(Executors.databaseEC),
      new FeeRefundsRepository.FutureImpl(feeRefundsRepository)(Executors.databaseEC),
      config,
      paymentService,
      discordHelper,
      new reports.ReportsRepository.FutureImpl(reportsRepository)(Executors.databaseEC),
      connextHelper,
      clientService,
      new PreimagesRepository.FutureImpl(preimagesRepository)(Executors.databaseEC)
    )(Executors.globalEC)
  }
}
