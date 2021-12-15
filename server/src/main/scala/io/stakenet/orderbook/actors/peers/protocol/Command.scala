package io.stakenet.orderbook.actors.peers.protocol

import java.time.Instant
import java.util.UUID

import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd.{ChannelFeePayment, PaymentRHash, RefundablePayment}
import io.stakenet.orderbook.models.trading.{Resolution, Trade, TradingOrder, TradingPair}

/**
 * A command strictly arrives from a websocket client, it means, that the
 * subclasses specify the available actions that the websocket client can perform.
 */
sealed trait Command extends Product with Serializable
sealed trait UncategorizedCommand extends Command
sealed trait SubscriptionCommand extends Command
sealed trait OrderCommand extends Command
sealed trait HistoricDataCommand extends Command
sealed trait OrderFeeCommand extends Command
sealed trait ChannelCommand extends Command

object Command {

  /**
   * When the received command can't be parsed this command will be used as a default
   */
  final case class InvalidCommand(reason: String) extends UncategorizedCommand
  final case class Ping() extends UncategorizedCommand
  final case class GetTradingPairs() extends UncategorizedCommand
  final case class CleanTradingPairOrders(pair: TradingPair) extends UncategorizedCommand

  final case class Subscribe(pair: TradingPair, retrieveOrdersSummary: Boolean) extends SubscriptionCommand
  final case class Unsubscribe(pair: TradingPair) extends SubscriptionCommand

  final case class GetHistoricTrades(limit: Int, lastSeenTrade: Option[Trade.Id], tradingPair: TradingPair)
      extends HistoricDataCommand // TODO: Enforce limit range
  final case class GetBarsPrices(
      tradingPair: TradingPair,
      resolution: Resolution,
      from: Instant,
      to: Instant,
      limit: Int
  ) extends HistoricDataCommand

  final case class PlaceOrder(order: TradingOrder, paymentHash: Option[PaymentRHash]) extends OrderCommand
  final case class GetOpenOrderById(id: OrderId) extends OrderCommand
  final case class GetOpenOrders(pair: TradingPair) extends OrderCommand
  final case class CancelOpenOrder(id: OrderId) extends OrderCommand
  final case class CancelMatchedOrder(id: OrderId) extends OrderCommand

  /**
   * When an order is matched, the order pair is moved to the trading state,
   * in this state, the peers are allowed to exchange messages.
   *
   * This command is for sending a message to the peer that has a matched order
   * against the given order that belongs to me.
   *
   * The server needs to route the message to the right peer.
   */
  final case class SendOrderMessage(orderMessage: OrderMessage) extends OrderCommand

  final case class GetInvoicePayment(currency: Currency, amount: Satoshis) extends OrderFeeCommand
  final case class GetConnextPaymentInformation(currency: Currency) extends OrderFeeCommand
  final case class GetRefundableAmount(currency: Currency, refundablePaymentList: List[RefundablePayment])
      extends OrderFeeCommand
  final case class RefundFee(currency: Currency, refundedFees: List[RefundablePayment]) extends OrderFeeCommand

  final case class GenerateInvoiceToRentChannel(channelFeePayment: ChannelFeePayment) extends ChannelCommand
  final case class GeneratePaymentHashToRentChannel(channelFeePayment: ChannelFeePayment) extends ChannelCommand
  final case class RentChannel(paymentRHash: PaymentRHash, payingCurrency: Currency) extends ChannelCommand
  final case class GetChannelStatus(channelId: UUID) extends ChannelCommand
  final case class GetFeeToRentChannel(channelFeePayment: ChannelFeePayment) extends ChannelCommand
  final case class GetFeeToExtendRentedChannel(
      channelId: UUID,
      payingCurrency: Currency,
      lifetimeSeconds: Long
  ) extends ChannelCommand
  final case class GenerateInvoiceToExtendRentedChannel(
      channelId: ChannelId.LndChannelId,
      payingCurrency: Currency,
      lifetimeSeconds: Long
  ) extends ChannelCommand
  final case class GeneratePaymentHashToExtendConnextRentedChannel(
      channelId: ChannelId.ConnextChannelId,
      payingCurrency: Currency,
      lifetimeSeconds: Long
  ) extends ChannelCommand
  final case class ExtendRentedChannelTime(paymentHash: PaymentRHash, payingCurrency: Currency) extends ChannelCommand
  final case class RegisterPublicKey(
      currency: Currency,
      nodePublicKey: Identifier.LndPublicKey
  ) extends UncategorizedCommand
  final case class RegisterPublicIdentifier(
      currency: Currency,
      publicIdentifier: Identifier.ConnextPublicIdentifier
  ) extends UncategorizedCommand
  final case class RegisterConnextChannelContractDeploymentFee(
      transactionHash: String
  ) extends ChannelCommand
  final case class GetConnextChannelContractDeploymentFee() extends ChannelCommand
}
