package helpers

import java.time.Instant

import io.stakenet.orderbook.models.ChannelIdentifier.{ConnextChannelAddress, LndOutpoint}
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, ClientPublicIdentifierId, ClientPublicKeyId, Identifier}
import io.stakenet.orderbook.models.lnd.{Fee, LndTxid, PaymentRHash}
import io.stakenet.orderbook.models.trading.{OrderSide, Trade, TradingPair}
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis, WalletId}
import org.scalatest.OptionValues._

import scala.util.Random

object Helpers {

  def randomPaymentHash(): PaymentRHash = {
    val data = Random.alphanumeric.take(32).mkString.getBytes
    val hexData = data.map("%02X".format(_)).mkString

    PaymentRHash.untrusted(hexData).value
  }

  def randomPublicKey(): Identifier.LndPublicKey = {
    val data = Random.alphanumeric.take(33).mkString.getBytes
    val hexData = data.map("%02X".format(_)).mkString

    Identifier.LndPublicKey.untrusted(hexData).value
  }

  def randomPublicIdentifier(): ConnextPublicIdentifier = {
    val data = Random.alphanumeric.take(50).mkString

    ConnextPublicIdentifier(s"vector$data")
  }

  def randomChannelAddress(): ConnextChannelAddress = {
    val data = Random.alphanumeric.take(40).mkString

    ConnextChannelAddress(s"0x$data").value
  }

  def randomClientPublicKey(currency: Currency = Currency.XSN): ClientLndPublicKey = {
    ClientLndPublicKey(ClientPublicKeyId.random(), randomPublicKey(), currency, ClientId.random())
  }

  def randomClientPublicIdentifier(currency: Currency = Currency.USDT): ClientConnextPublicIdentifier = {
    ClientConnextPublicIdentifier(
      ClientPublicIdentifierId.random(),
      randomPublicIdentifier(),
      currency,
      ClientId.random()
    )
  }

  def randomWalletId(): WalletId = {
    val data = Random.alphanumeric.take(40).mkString.toLowerCase

    WalletId(data).value
  }

  def randomOutpoint(): LndOutpoint = {
    val data = Random.alphanumeric.take(32).mkString.getBytes

    LndOutpoint(LndTxid(data), Random.nextInt(5))
  }

  def randomTrade(pair: TradingPair): Trade = {
    val existingOrder = randomOrder(pair, OrderSide.Buy)
    val executingOrder = randomOrder(pair, OrderSide.Sell)

    Trade.from(pair)(executingOrder, existingOrder)
  }

  def randomOrder(pair: TradingPair, side: OrderSide): pair.LimitOrder = {
    val orderId = OrderId.random()
    val funds = randomSatoshis()
    val price = randomSatoshis()

    pair.Order.limit(side, orderId, funds, price)
  }

  def randomSatoshis(): Satoshis = {
    val value = (Satoshis.MaxValueDecimal * Random.nextDouble()).setScale(18)

    Satoshis.from(value).value
  }

  def randomFee(currency: Currency): Fee = {
    Fee(currency, randomPaymentHash(), randomSatoshis(), None, Instant.now, 0.0001)
  }

  def asSatoshis(amount: String): Satoshis = Satoshis.from(BigDecimal(amount)).value
}
