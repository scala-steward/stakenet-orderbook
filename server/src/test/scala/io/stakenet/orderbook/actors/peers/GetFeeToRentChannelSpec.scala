package io.stakenet.orderbook.actors.peers

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.actors.peers.protocol.Command.{GenerateInvoiceToRentChannel, GetFeeToRentChannel}
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.{CommandFailed, GetFeeToRentChannelResponse}
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models.lnd.ChannelFees
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{Currency, Satoshis, TradingPairPrice}
import io.stakenet.orderbook.repositories.trades.TradesRepository
import org.mockito.MockitoSugar._
import org.scalatest.OptionValues._

import scala.concurrent.duration._

class GetFeeToRentChannelSpec extends PeerSpecBase("GetFeeToRentChannelSpec") {
  "GetFeeToRentChannel" should {
    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> ChannelFees(
          Currency.XSN,
          Helpers.asSatoshis("0.02962962936"),
          Helpers.asSatoshis("0.00000493827156"),
          Helpers.asSatoshis("0.000493827156")
        ),
        Currency.BTC -> ChannelFees(
          Currency.BTC,
          Helpers.asSatoshis("0.019440000176904001"),
          Helpers.asSatoshis("0.000183967201674101"),
          Helpers.asSatoshis("0.0038880000353808")
        ),
        Currency.LTC -> ChannelFees(
          Currency.LTC,
          Helpers.asSatoshis("0.019440000176904001"),
          Helpers.asSatoshis("0.000003240000029484"),
          Helpers.asSatoshis("0.0003240000029484")
        )
      )

      s"respond with GetFeeToRentChannelResponse for $currency" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(currency = currency, payingCurrency = otherCurrency, lifeTimeSeconds = duration)

          val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))

          alice.actor ! WebSocketIncomingMessage(requestId, GetFeeToRentChannel(channelFeePayment))

          val fee = fees.get(currency).value
          val expected = GetFeeToRentChannelResponse(
            fee.totalFee,
            fee.rentingFee + fee.forceClosingFee,
            fee.transactionFee
          )
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), expected))
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> ChannelFees(
          Currency.XSN,
          Helpers.asSatoshis("0.019440000176904001"),
          Helpers.asSatoshis("0.000183967201674101"),
          Helpers.asSatoshis("0.0038880000353808")
        ),
        Currency.BTC -> ChannelFees(
          Currency.BTC,
          Helpers.asSatoshis("0.02962962936"),
          Helpers.asSatoshis("0.00000493827156"),
          Helpers.asSatoshis("0.000493827156")
        ),
        Currency.LTC -> ChannelFees(
          Currency.LTC,
          Helpers.asSatoshis("0.02962962936"),
          Helpers.asSatoshis("0.00000493827156"),
          Helpers.asSatoshis("0.000493827156")
        )
      )

      s"respond with GetFeeToRentChannelResponse paying with $currency" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val otherCurrency = Currency.forLnd.filter(_ != currency).head
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = otherCurrency, lifeTimeSeconds = duration)

          val tradingPair = TradingPair.from(channelFeePayment.currency, channelFeePayment.payingCurrency)
          val tradingPairPrice = TradingPairPrice(tradingPair, Helpers.asSatoshis("1.23456789"), Instant.now)
          when(tradesRepository.getLastPrice(tradingPair)).thenReturn(Some(tradingPairPrice))

          alice.actor ! WebSocketIncomingMessage(requestId, GetFeeToRentChannel(channelFeePayment))

          val fee = fees.get(currency).value
          val expected = GetFeeToRentChannelResponse(
            fee.totalFee,
            fee.rentingFee + fee.forceClosingFee,
            fee.transactionFee
          )
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), expected))
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      val fees = Map(
        Currency.XSN -> ChannelFees(
          Currency.XSN,
          Helpers.asSatoshis("0.02439996"),
          Helpers.asSatoshis("0.000004"),
          Helpers.asSatoshis("0.00000004")
        ),
        Currency.BTC -> ChannelFees(
          Currency.BTC,
          Helpers.asSatoshis("0.02879952"),
          Helpers.asSatoshis("0.00022712"),
          Helpers.asSatoshis("0.00000048")
        ),
        Currency.LTC -> ChannelFees(
          Currency.LTC,
          Helpers.asSatoshis("0.02439996"),
          Helpers.asSatoshis("0.000004"),
          Helpers.asSatoshis("0.00000004")
        )
      )

      s"allow to rent a $currency channel paying with $currency" in {
        val tradesRepository = mock[TradesRepository.Blocking]

        withSinglePeer(tradesRepository = tradesRepository) { alice =>
          val requestId = "id"
          val duration = 5.days.toSeconds
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          alice.actor ! WebSocketIncomingMessage(requestId, GetFeeToRentChannel(channelFeePayment))

          val fee = fees.get(currency).value
          val expected = GetFeeToRentChannelResponse(
            fee.totalFee,
            fee.rentingFee + fee.forceClosingFee,
            fee.transactionFee
          )
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), expected))
        }
      }
    }

    Currency.forLnd.foreach { currency =>
      s"fail when duration is more than 7 days for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 7.days.toSeconds + 1
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(payingCurrency = currency, currency = currency, lifeTimeSeconds = duration)

          alice.actor ! WebSocketIncomingMessage(requestId, GetFeeToRentChannel(channelFeePayment))

          val error = "Max duration is 7 days"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }

      s"fail when max capacity is more than 10k USD for $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val duration = 7.days.toSeconds
          val capacity = Satoshis.from(BigDecimal(10001)).value
          val channelFeePayment = SampleChannels
            .newChannelFeePayment()
            .copy(
              payingCurrency = currency,
              currency = currency,
              lifeTimeSeconds = duration,
              capacity = capacity
            )

          alice.actor ! WebSocketIncomingMessage(requestId, GetFeeToRentChannel(channelFeePayment))

          val error = s"Max capacity is 10000.00000000 $currency(10000 USD)"
          alice.client.expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(error)))
        }
      }
    }

    Currency.values.diff(Currency.forLnd).foreach { currency =>
      s"return an error paying with $currency" in {
        withSinglePeer() { alice =>
          val requestId = "id"
          val channelFeePayment = SampleChannels.newChannelFeePayment().copy(payingCurrency = currency)

          alice.actor ! WebSocketIncomingMessage(requestId, GenerateInvoiceToRentChannel(channelFeePayment))
          alice.client
            .expectMsg(WebSocketOutgoingMessage(1, Some(requestId), CommandFailed(s"$currency not supported")))
        }
      }
    }
  }

}
