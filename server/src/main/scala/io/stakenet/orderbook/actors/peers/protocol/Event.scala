package io.stakenet.orderbook.actors.peers.protocol

import java.time.Instant

import io.stakenet.orderbook.actors.peers.results
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.models._

sealed trait Event extends Product with Serializable

object Event {
  sealed trait CommandResponse extends Event

  object CommandResponse {

    // Any command could fail, timeouts and exceptions can occur
    sealed trait CommandFailed extends CommandResponse

    object CommandFailed {

      case class Reason(reason: String) extends CommandFailed

      case class ServerInMaintenance() extends CommandFailed

      def apply(reason: String): CommandFailed = CommandFailed.Reason(reason)
    }

    sealed trait ChannelStatus

    object ChannelStatus {
      case class Lnd(
          status: lnd.ChannelStatus,
          expiresAt: Option[Instant],
          closingType: Option[String],
          closedBy: Option[String],
          closedOn: Option[Instant]
      ) extends ChannelStatus

      case class Connext(
          status: ConnextChannelStatus,
          expiresAt: Option[Instant]
      ) extends ChannelStatus
    }

    final case class PingResponse() extends CommandResponse

    final case class GetTradingPairsResponse(tradingPairs: List[TradingPair], paysFees: Boolean) extends CommandResponse
    final case class SubscribeResponse(
        pair: TradingPair,
        bidsSummary: List[OrderSummary],
        asksSummary: List[OrderSummary]
    ) extends CommandResponse
    final case class UnsubscribeResponse(pair: TradingPair) extends CommandResponse

    final case class GetOpenOrdersResponse(
        pair: TradingPair,
        bidsSummary: List[OrderSummary],
        asksSummary: List[OrderSummary]
    ) extends CommandResponse
    final case class GetHistoricTradesResponse(trades: List[Trade]) extends CommandResponse
    final case class GetBarsPricesResponse(bars: List[Bars]) extends CommandResponse

    final case class PlaceOrderResponse(result: results.PlaceOrderResult) extends CommandResponse
    final case class GetOpenOrderByIdResponse(result: Option[TradingOrder]) extends CommandResponse
    final case class CancelOpenOrderResponse(result: Option[TradingOrder]) extends CommandResponse
    final case class CancelMatchedOrderResponse(result: Option[Trade]) extends CommandResponse
    final case class SendOrderMessageResponse() extends CommandResponse
    final case class CleanTradingPairOrdersResponse(
        pair: TradingPair,
        openOrdersRemoved: List[OrderId],
        matchedOrdersRemoved: List[OrderId]
    ) extends CommandResponse
    final case class GetInvoicePaymentResponse(
        currency: Currency,
        noFeeRequired: Boolean,
        paymentRequest: Option[String] // None when noFeeRequired is true
    ) extends CommandResponse
    final case class GetConnextPaymentInformationResponse(
        currency: Currency,
        noFeeRequired: Boolean,
        publicIdentifier: String,
        paymentHash: Option[PaymentRHash] // None when noFeeRequired is true
    ) extends CommandResponse
    final case class GenerateInvoiceToRentChannelResponse(channelFeePayment: ChannelFeePayment, paymentRequest: String)
        extends CommandResponse
    final case class GeneratePaymentHashToRentChannelResponse(
        channelFeePayment: ChannelFeePayment,
        paymentHash: PaymentRHash
    ) extends CommandResponse
    final case class RentChannelResponse(
        paymentHash: PaymentRHash,
        clientIdentifier: Identifier,
        channelId: ChannelId,
        channelIdentifier: ChannelIdentifier
    ) extends CommandResponse
    final case class GetChannelStatusResponse(
        channelId: ChannelId,
        status: ChannelStatus
    ) extends CommandResponse
    final case class GetFeeToRentChannelResponse(
        fee: Satoshis,
        rentingFee: Satoshis,
        onChainFees: Satoshis
    ) extends CommandResponse

    final case class RefundFeeResponse(
        currency: Currency,
        amount: Satoshis,
        refundedFees: List[RefundablePayment],
        refundedOn: Instant
    ) extends CommandResponse
    final case class GetRefundableAmountResponse(
        currency: Currency,
        amount: Satoshis
    ) extends CommandResponse
    final case class GetFeeToExtendRentedChannelResponse(fee: Satoshis) extends CommandResponse
    final case class GenerateInvoiceToExtendRentedChannelResponse(
        channelId: ChannelId.LndChannelId,
        payingCurrency: Currency,
        lifetimeSeconds: Long,
        paymentRequest: String
    ) extends CommandResponse
    final case class GeneratePaymentHashToExtendConnextRentedChannelResponse(
        channelId: ChannelId.ConnextChannelId,
        payingCurrency: Currency,
        lifetimeSeconds: Long,
        paymentHash: PaymentRHash
    ) extends CommandResponse
    final case class ExtendRentedChannelTimeResponse(
        paymentHash: PaymentRHash,
        channelId: ChannelId,
        lifeTimeSeconds: Long
    ) extends CommandResponse
    final case class RegisterPublicKeyResponse(
        currency: Currency,
        nodePublicKey: Identifier.LndPublicKey
    ) extends CommandResponse
    final case class RegisterPublicIdentifierResponse(
        currency: Currency,
        publicIdentifier: Identifier.ConnextPublicIdentifier
    ) extends CommandResponse
    final case class RegisterConnextChannelContractDeploymentFeeResponse(
        transactionHash: String
    ) extends CommandResponse
    final case class GetConnextChannelContractDeploymentFeeResponse(
        hubAddress: String,
        amount: Satoshis
    ) extends CommandResponse
  }

  sealed trait ServerEvent extends Event

  object ServerEvent {
    final case class MyOrderMatched(trade: Trade, orderMatchedWith: TradingOrder) extends ServerEvent
    final case class MyMatchedOrderCanceled(trade: Trade) extends ServerEvent
    // events produced for trading pair subscribers
    final case class OrderPlaced(order: TradingOrder) extends ServerEvent
    final case class OrderCanceled(order: TradingOrder) extends ServerEvent
    final case class OrdersMatched(trade: Trade) extends ServerEvent
    // Event produced by one peer to another peer which have a trade in progress
    final case class NewOrderMessage(orderId: OrderId, message: Vector[Byte]) extends ServerEvent
    final case class SwapSuccess(trade: Trade) extends ServerEvent
    final case class SwapFailure(trade: Trade) extends ServerEvent
    final case class MaintenanceInProgress() extends ServerEvent
    final case class MaintenanceCompleted() extends ServerEvent
  }
}
