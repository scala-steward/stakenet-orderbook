package io.stakenet.orderbook.models.trading

import enumeratum._
import io.stakenet.orderbook.models._
import play.api.libs.json.{JsError, JsSuccess, Reads}

/**
 * Represents a pair that can be traded, there is a limited known number of such pairs, for example:
 * - XSN_BTC
 * - XSN_LTC
 *
 * The names are based on how the pairs are displayed on coinmarketcap, see https://coinmarketcap.com/currencies/stakenet/#markets
 * For example, for the XSN_BTC pair, there two currencies involved:
 * - BTC is the principal currency, which is used to specify most values in orders/trades.
 * - XSN is the secondary currency.
 */
sealed abstract class TradingPair extends EnumEntry with Product with Serializable with Ordered[TradingPair] { self =>
  // TODO: check at compile time
  //require(principal != secondary, s"A trading pair must have different currencies, only $principal found")

  def secondary: Currency
  def principal: Currency
  def buyFundsInterval: Satoshis.InclusiveInterval
  def buyPriceInterval: Satoshis.InclusiveInterval
  def sellFundsInterval: Satoshis.InclusiveInterval
  def sellPriceInterval: Satoshis.InclusiveInterval
  def buyFee: BigDecimal
  def sellFee: BigDecimal

  def use(candidate: TradingPair#Order): Option[Order] = {
    if (candidate.tradingPair == this) {
      Option(candidate.asInstanceOf[Order])
    } else {
      None
    }
  }

  def useLimitOrder(candidate: TradingPair#Order): Option[LimitOrder] = {
    use(candidate) match {
      case Some(x: LimitOrder) => Some(x)
      case _ => None
    }
  }

  override def compare(that: TradingPair): Int = this.entryName.compare(that.entryName)

  /**
   * An order tied for this trading pair.
   */
  sealed trait Order extends Product with Serializable {
    def tradingPair: TradingPair = self
    def id: OrderId
    def side: OrderSide

    def feePercent: BigDecimal = side match {
      case OrderSide.Buy => buyFee
      case OrderSide.Sell => sellFee
    }

    def feeCurrency: Currency = sellingCurrency

    // the currency you have
    def sellingCurrency: Currency = side match {
      case OrderSide.Buy => principal
      case OrderSide.Sell => secondary
    }

    // The currency you are trying to get
    def buyingCurrency: Currency = side match {
      case OrderSide.Buy => secondary
      case OrderSide.Sell => principal
    }

    // The funds to trade, specified in the currency you own, for buy order is the principal, for sell order is the secondary.
    def funds: Satoshis

    /**
     * @return true if this order can be matched with the given order
     */
    def matches(other: LimitOrder): Boolean

    override def toString: String = this match {
      case LimitOrder(side, details) =>
        s"$side ${details.funds.toString(sellingCurrency)} at 1 $sellingCurrency = ${details.price.toString(buyingCurrency)}"
      case MarketOrder(_, side, funds) =>
        s"$side ${funds.toString(sellingCurrency)} at 1 $sellingCurrency = market price $buyingCurrency"
    }
  }

  object Order {

    // compare by price, breaking ties by funds (highest first) and then by id
    // Satoshis.MaxValue - o.funds is a trick to negate the price as Satoshis can't be negative
    implicit val orderingBySmallerPriceHigherFunds: Ordering[self.LimitOrder] =
      (order1: self.LimitOrder, order2: self.LimitOrder) => {
        Ordering[(Satoshis, Satoshis, OrderId)]
          .on[self.LimitOrder](o => (o.details.price, Satoshis.MaxValue - o.funds, o.id))
          .compare(order1, order2)
      }

    def limit(side: OrderSide, id: OrderId, funds: Satoshis, price: Satoshis): LimitOrder = {
      LimitOrder(side, LimitOrderDetails(id, funds = funds, price = price))
    }

    def market(side: OrderSide, id: OrderId, funds: Satoshis): MarketOrder = {
      MarketOrder(id, side, funds)
    }
  }

  /**
   * Order to buy/sell the [[principal]] currency at the market price.
   */
  case class MarketOrder(id: OrderId, side: OrderSide, funds: Satoshis) extends Order {

    override def matches(other: LimitOrder): Boolean = {
      this.side != other.side
    }
  }

  /**
   * Order to buy/sell the [[principal]] currency at the given price.
   *
   * 100_000_000 (satoshis) [[principal]] = price [[secondary]]
   */
  case class LimitOrder(side: OrderSide, details: LimitOrderDetails) extends Order {
    override def id: OrderId = details.id
    override def funds: Satoshis = details.funds

    /**
     * A Sell order will take all buy orders with a higher price
     *
     * A Buy order will take all sell orders with lower price
     */
    override def matches(other: LimitOrder): Boolean = {
      (this.side, other.side) match {
        case (OrderSide.Sell, OrderSide.Buy) => details.price <= other.details.price
        case (OrderSide.Buy, OrderSide.Sell) => details.price >= other.details.price
        case _ => false
      }
    }
  }

  /**
   * Details for a limit order
   *
   * @param id order id
   * @param funds order funds the amount you own, for buy orders is the principal currency, for sell orders is the secondary
   * @param price order price in the principal currency, for XSN_BTC, the price is in BTC
   */
  case class LimitOrderDetails(id: OrderId, funds: Satoshis, price: Satoshis)

}

object TradingPair extends Enum[TradingPair] {

  private val DefaultInterval = Satoshis.InclusiveInterval(
    from = satoshis(BigDecimal("0.0000001")),
    to = satoshis(BigDecimal("100000000.00000000"))
  )

  val feeless = List(XSN_WETH, BTC_WETH, BTC_USDT, ETH_BTC, BTC_USDC, ETH_USDC)
  val values = findValues

  final case object XSN_BTC extends TradingPair {
    override def secondary: Currency = Currency.XSN
    override def principal: Currency = Currency.BTC

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // buying XSN costs a small fee
    override def buyFee: BigDecimal = 0.0001
    // selling XSN is not free
    override def sellFee: BigDecimal = 0.0001
  }

  final case object XSN_LTC extends TradingPair {
    override def secondary: Currency = Currency.XSN
    override def principal: Currency = Currency.LTC

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // buying XSN costs a small fee
    override def buyFee: BigDecimal = 0.0001
    // selling XSN is not free
    override def sellFee: BigDecimal = 0.0001
  }

  final case object LTC_BTC extends TradingPair {
    override def secondary: Currency = Currency.LTC
    override def principal: Currency = Currency.BTC

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying LTC, this percent is for BTC
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling LTC, this percent is for LTC
    override def sellFee: BigDecimal = 0.0001
  }

  final case object XSN_WETH extends TradingPair {
    override def secondary: Currency = Currency.XSN
    override def principal: Currency = Currency.WETH

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying XSN, this percent is for WETH
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling XSN, this percent is for XSN
    override def sellFee: BigDecimal = 0.0001
  }

  final case object BTC_WETH extends TradingPair {
    override def secondary: Currency = Currency.BTC
    override def principal: Currency = Currency.WETH

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying BTC, this percent is for WETH
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling BTC, this percent is for BTC
    override def sellFee: BigDecimal = 0.0001
  }

  final case object BTC_USDT extends TradingPair {
    override def secondary: Currency = Currency.BTC
    override def principal: Currency = Currency.USDT

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying BTC, this percent is for BTC
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling BTC, this percent is for USDT
    override def sellFee: BigDecimal = 0.0001
  }

  final case object ETH_BTC extends TradingPair {
    override def secondary: Currency = Currency.ETH
    override def principal: Currency = Currency.BTC

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying BTC, this percent is for BTC
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling BTC, this percent is for USDT
    override def sellFee: BigDecimal = 0.0001
  }

  final case object BTC_USDC extends TradingPair {
    override def secondary: Currency = Currency.BTC
    override def principal: Currency = Currency.USDC

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying BTC, this percent is for BTC
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling BTC, this percent is for USDT
    override def sellFee: BigDecimal = 0.0001
  }

  final case object ETH_USDC extends TradingPair {
    override def secondary: Currency = Currency.ETH
    override def principal: Currency = Currency.USDC

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying BTC, this percent is for BTC
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling BTC, this percent is for USDT
    override def sellFee: BigDecimal = 0.0001
  }

  final case object XSN_ETH extends TradingPair {
    override def secondary: Currency = Currency.XSN
    override def principal: Currency = Currency.ETH

    // TODO: Define the right intervals
    override def buyFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def buyPriceInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellFundsInterval: Satoshis.InclusiveInterval = DefaultInterval
    override def sellPriceInterval: Satoshis.InclusiveInterval = DefaultInterval

    // The fee percent for buying BTC, this percent is for BTC
    override def buyFee: BigDecimal = 0.0001
    // The fee percent for selling BTC, this percent is for USDT
    override def sellFee: BigDecimal = 0.0001
  }

  private def satoshis(value: BigDecimal): Satoshis = {
    Satoshis.from(value).getOrElse(throw new RuntimeException("Impossible, invalid satoshis"))
  }

  def from(currency1: Currency, currency2: Currency): TradingPair = {
    val pair1 = s"${currency1.enumEntry}_${currency2.enumEntry}"
    val pair2 = s"${currency2.enumEntry}_${currency1.enumEntry}"

    List(pair1, pair2)
      .flatMap(TradingPair.withNameInsensitiveOption)
      .headOption
      .getOrElse(throw new RuntimeException(s"Impossible, invalid trading pair : $pair1"))
  }

  implicit val reads: Reads[TradingPair] = Reads { json =>
    json.validate[String].flatMap { string =>
      TradingPair
        .withNameInsensitiveOption(string)
        .map(JsSuccess.apply(_))
        .getOrElse {
          JsError.apply("Invalid trading pair")
        }
    }
  }
}
