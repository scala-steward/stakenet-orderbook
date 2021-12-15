package io.stakenet.orderbook.repositories.channels

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, Identifier}
import io.stakenet.orderbook.models.connext.ConnextChannel
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{ChannelId, ConnextChannelStatus, Currency, Satoshis}
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

import scala.concurrent.duration._

class ChannelsPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new ChannelsPostgresRepository(database)

  "createChannelFeePayment" should {
    val channelFeePayment = SampleChannels.newChannelFeePayment()
    "create a new channelFeePayment" in {
      repository.createChannelFeePayment(
        channelFeePayment,
        Helpers.randomPaymentHash(),
        channelFeePayment.fees.totalFee
      )

      succeed
    }

    "Fail when try to create a repeated channelFeePayment" in {
      val paymentHash = Helpers.randomPaymentHash()
      repository.createChannelFeePayment(channelFeePayment, paymentHash, channelFeePayment.fees.totalFee)

      val ex = intercept[PSQLException] {
        repository.createChannelFeePayment(channelFeePayment, paymentHash, channelFeePayment.fees.totalFee)
      }

      ex.getMessage must be("Payment hash already exists")
    }
  }

  "findChannelFeePayment by paymentHash and currency" should {
    "find a channelFeePayment" in {
      val price = SampleChannels.XsnBtcPrice
      val channelFeePayment = SampleChannels.newChannelFeePayment().copy(paidFee = price)
      val paymentRHash = Helpers.randomPaymentHash()
      repository.createChannelFeePayment(channelFeePayment, paymentRHash, price)
      val repChannel = repository.findChannelFeePayment(paymentRHash, channelFeePayment.payingCurrency)

      repChannel.value must be(channelFeePayment)
    }

    "return a empty value when the channelFeePayment does not exist" in {
      val result = repository.findChannelFeePayment(Helpers.randomPaymentHash(), Currency.XSN)

      result must be(empty)
    }

  }

  "findChannelFeePayment by channelId(lnd)" should {
    "find a channelFeePayment" in {
      val price = SampleChannels.XsnBtcPrice
      val channelFeePayment = SampleChannels.newChannelFeePayment().copy(currency = Currency.XSN, paidFee = price)
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val channel = SampleChannels
        .newChannel()
        .copy(
          paymentRHash = Helpers.randomPaymentHash(),
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicKeyId = clientPublicKey.clientPublicKeyId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, price)
      repository.createChannel(channel)
      val repChannel = repository.findChannelFeePayment(channel.channelId)

      repChannel.value must be(channelFeePayment)
    }

    "return a empty value when the channelFeePayment does not exist" in {
      val repChannel = repository.findChannelFeePayment(ChannelId.LndChannelId.random())

      repChannel must be(empty)
    }

  }

  "findChannelFeePayment by channelId(connext)" should {
    "find a channelFeePayment" in {
      val price = Helpers.asSatoshis("0.0000001")
      val channelFeePayment = SampleChannels.newChannelFeePayment().copy(currency = Currency.USDC, paidFee = price)
      val clientPublicIdentifier = createClientPublicIdentifier(Helpers.randomPublicIdentifier(), Currency.USDC)
      val channel = SampleChannels
        .newConnextChannel()
        .copy(
          paymentRHash = Helpers.randomPaymentHash(),
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, price)
      repository.createChannel(channel, transactionHash = "")
      val repChannel = repository.findChannelFeePayment(channel.channelId)

      repChannel.value must be(channelFeePayment)
    }

    "return a empty value when the channelFeePayment does not exist" in {
      val repChannel = repository.findChannelFeePayment(ChannelId.ConnextChannelId.random())

      repChannel must be(empty)
    }

  }

  "findChannelFeePayment by outpoint" should {
    "find a channelFeePayment" in {
      val price = SampleChannels.XsnBtcPrice
      val channelFeePayment = SampleChannels.newChannelFeePayment().copy(paidFee = price)
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val outpoint = Helpers.randomOutpoint()
      val channel = SampleChannels
        .newChannel()
        .withChannelPoint(outpoint)
        .copy(
          paymentRHash = Helpers.randomPaymentHash(),
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicKeyId = clientPublicKey.clientPublicKeyId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, price)
      repository.createChannel(channel)
      repository.updateChannelPoint(channel.channelId, outpoint)
      val repChannel = repository.findChannelFeePayment(outpoint)

      repChannel.value must be(channelFeePayment)
    }

    "return a empty value when the channelFeePayment does not exist" in {
      val repChannel = repository.findChannelFeePayment(Helpers.randomOutpoint())

      repChannel must be(empty)
    }
  }

  "createChannel(LND)" should {
    "create a new channel" in {
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val channel = SampleChannels
        .newChannel()
        .copy(payingCurrency = channelFeePayment.payingCurrency, clientPublicKeyId = clientPublicKey.clientPublicKeyId)

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
      repository.createChannel(channel)
      succeed
    }

    "Fail when try to create a repeated channel" in {
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val channel = SampleChannels
        .newChannel()
        .copy(payingCurrency = channelFeePayment.payingCurrency, clientPublicKeyId = clientPublicKey.clientPublicKeyId)

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
      repository.createChannel(channel)

      val ex = intercept[PSQLException] {
        repository.createChannel(channel)
      }

      ex.getMessage must be("The channel already exists")
    }

    "Fail when try to create a channel with an existing payment_hash" in {
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val channel = SampleChannels
        .newChannel()
        .copy(payingCurrency = channelFeePayment.payingCurrency, clientPublicKeyId = clientPublicKey.clientPublicKeyId)
      val channel1 = SampleChannels
        .newChannel()
        .copy(
          paymentRHash = channel.paymentRHash,
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicKeyId = clientPublicKey.clientPublicKeyId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
      repository.createChannel(channel)

      val ex = intercept[PSQLException] {
        repository.createChannel(channel1)
      }

      ex.getMessage must be("The payment hash already exists")
    }

    "create a channels with the same payment hash in different currencies" in {
      val channelFeePayment1 = SampleChannels
        .newChannelFeePayment()
        .copy(currency = Currency.LTC, payingCurrency = Currency.XSN)
      val channelFeePayment2 = SampleChannels
        .newChannelFeePayment()
        .copy(currency = Currency.BTC, payingCurrency = Currency.LTC)
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val channel1 = SampleChannels
        .newChannel()
        .copy(payingCurrency = channelFeePayment1.payingCurrency, clientPublicKeyId = clientPublicKey.clientPublicKeyId)
      val channel2 = SampleChannels
        .newChannel()
        .copy(payingCurrency = channelFeePayment2.payingCurrency, clientPublicKeyId = clientPublicKey.clientPublicKeyId)

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel1)
      repository.createChannel(channel2)

      succeed
    }

    "fail when the channel fee payment does not exist" in {
      val clientPublicKey = createClientPublicKey(Helpers.randomPublicKey(), Currency.XSN)
      val channel = SampleChannels.newChannel().copy(clientPublicKeyId = clientPublicKey.clientPublicKeyId)

      val error = intercept[PSQLException] {
        repository.createChannel(channel)
      }

      error.getMessage mustBe "Fee payment not found"
    }

    "fail when the client public key does not exist" in {
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicKey = Helpers.randomClientPublicKey()
      val channel = SampleChannels
        .newChannel()
        .copy(payingCurrency = channelFeePayment.payingCurrency, clientPublicKeyId = clientPublicKey.clientPublicKeyId)

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)

      val error = intercept[PSQLException] {
        repository.createChannel(channel)
      }

      error.getMessage mustBe "Client public key not found"
    }
  }

  "createChannel(Connext)" should {
    "create a new channel" in {
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )
      val channel = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
      repository.createChannel(channel, transactionHash)
      succeed
    }

    "Fail when try to create a repeated channel" in {
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )
      val channel = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
      repository.createChannel(channel, transactionHash)

      val ex = intercept[PSQLException] {
        repository.createChannel(channel, transactionHash)
      }

      ex.getMessage must be(s"Channel ${channel.channelId} already exists")
    }

    "Fail when try to create a channel with an existing payment_hash" in {
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          paymentRHash = channel1.paymentRHash,
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )

      repository.createChannelFeePayment(channelFeePayment, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash)

      val ex = intercept[PSQLException] {
        repository.createChannel(channel2, transactionHash)
      }

      ex.getMessage must be(s"Channel for (${channel1.paymentRHash} ${channel1.payingCurrency}) already exist")
    }

    "create a channels with the same payment hash in different currencies" in {
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels
        .newChannelFeePayment()
        .copy(currency = Currency.LTC, payingCurrency = Currency.XSN)
      val channelFeePayment2 = SampleChannels
        .newChannelFeePayment()
        .copy(currency = Currency.BTC, payingCurrency = Currency.LTC)
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          paymentRHash = channel1.paymentRHash,
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash)
      repository.createChannel(channel2, transactionHash)

      succeed
    }

    "fail when the channel fee payment does not exist" in {
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )
      val channel = SampleChannels
        .newConnextChannel()
        .copy(clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId)

      val error = intercept[PSQLException] {
        repository.createChannel(channel, transactionHash)
      }

      error.getMessage mustBe s"Fee payment (${channel.paymentRHash} ${channel.payingCurrency}) not found"
    }

    "fail when the client public key does not exist" in {
      val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment = SampleChannels.newChannelFeePayment()
      val clientPublicIdentifier = Helpers.randomClientPublicIdentifier()
      val channel = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
        )

      repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)

      val error = intercept[PSQLException] {
        repository.createChannel(channel, transactionHash)
      }

      error.getMessage mustBe s"public identifier ${channel.clientPublicIdentifierId} not found"
    }
  }

  "findChannel by channelId" should {
    "get a channel" in {
      val channel = createChannel(ChannelStatus.Active)

      val response = repository.findChannel(channel.channelId)

      response.value must be(channel)
    }

    "fail when the channel does not exist" in {
      createChannel(ChannelStatus.Active)

      val response = repository.findChannel(ChannelId.LndChannelId.random())

      response must be(empty)
    }
  }

  "findConnextChannel by channelId" should {
    "get a channel" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)

      val response = repository.findConnextChannel(channel.channelId)

      response.value must be(channel)
    }

    "fail when the channel does not exist" in {
      createConnextChannel(ConnextChannelStatus.Active)

      val response = repository.findConnextChannel(ChannelId.ConnextChannelId.random())

      response must be(empty)
    }
  }

  "updateChannelStatus" should {
    "update a channel from Opening to Active" in {
      val channel = createChannel(ChannelStatus.Opening)
      val channelUpdated = channel.copy(status = ChannelStatus.Active)

      repository.updateChannelStatus(channel.channelId, ChannelStatus.Active)
      val result = repository.findChannel(channel.channelId)
      result.value must be(channelUpdated)
    }

    "update a channel from Active to Closing" in {
      val channel = createChannel(ChannelStatus.Active)
      val channelUpdated = channel.copy(status = ChannelStatus.Closing)

      repository.updateChannelStatus(channel.channelId, ChannelStatus.Closing)
      val result = repository.findChannel(channel.channelId)
      result.value must be(channelUpdated)
    }

    "Fail when the channel id does not exist" in {
      createChannel(ChannelStatus.Opening)

      intercept[AssertionError] {
        repository.updateChannelStatus(ChannelId.LndChannelId.random(), ChannelStatus.Active)
      }
    }
  }

  "updateChannelPoint" should {
    "update the channel point " in {
      val outpoint = Helpers.randomOutpoint()
      val channel = createChannel(ChannelStatus.Opening).withChannelPoint(outpoint)

      repository.updateChannelPoint(channel.channelId, outpoint)
      val result = repository.findChannel(channel.channelId)
      result.value must be(channel)
    }

    "Fail when the channel id does not exist" in {
      createChannel(ChannelStatus.Opening)

      intercept[AssertionError] {
        repository.updateChannelPoint(ChannelId.LndChannelId.random(), Helpers.randomOutpoint())
      }
    }
  }

  "updateActiveChannel by channelId" should {
    "update the channel to active" in {
      val outpoint = Helpers.randomOutpoint()
      val createdAt = Instant.now
      val expiresAt = createdAt.plusSeconds(259200)
      val channel = createChannel(ChannelStatus.Opening)
        .withChannelPoint(outpoint)
        .copy(createdAt = Some(createdAt), expiresAt = Some(expiresAt), status = ChannelStatus.Active)

      repository.updateChannelPoint(channel.channelId, outpoint)
      repository.updateActiveChannel(channel.channelId, createdAt, expiresAt)
      val result = repository.findChannel(channel.channelId)
      result.value must be(channel)
    }

    "Fail when the channel id does not exist" in {
      createChannel(ChannelStatus.Opening)

      intercept[AssertionError] {
        repository.updateActiveChannel(ChannelId.LndChannelId.random(), Instant.now, Instant.now)
      }
    }
  }

  "updateActiveChannel by outpoint" should {
    "update the channel to active" in {
      val outpoint = Helpers.randomOutpoint()
      val createdAt = Instant.now
      val expiresAt = createdAt.plusSeconds(259200)
      val channel = createChannel(ChannelStatus.Opening)
        .withChannelPoint(outpoint)
        .copy(createdAt = Some(createdAt), expiresAt = Some(expiresAt), status = ChannelStatus.Active)

      repository.updateChannelPoint(channel.channelId, outpoint)
      repository.updateActiveChannel(outpoint, createdAt, expiresAt)
      val result = repository.findChannel(channel.channelId)
      result.value must be(channel)
    }

    "Fail when the channel id does not exist" in {
      createChannel(ChannelStatus.Opening)

      intercept[AssertionError] {
        repository.updateActiveChannel(Helpers.randomOutpoint(), Instant.now, Instant.now)
      }
    }
  }

  "getExpiredChannels" should {
    "get a list of expired channels" in {
      val channel = createChannel(ChannelStatus.Opening)
      val expiresAt = Instant.now
      val createdAt = expiresAt.minusSeconds(50000)
      repository.updateChannelPoint(channel.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel.channelId, createdAt, expiresAt)
      val channelUpdated = repository.findChannel(channel.channelId).value
      // Add an active channel
      val channel2 = createChannel(ChannelStatus.Opening)
      val expiresAt2 = Instant.now.plusSeconds(60000)
      val createdAt2 = expiresAt.minusSeconds(120000)
      repository.updateChannelPoint(channel2.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel2.channelId, createdAt2, expiresAt2)
      // Add a closed  channel
      val channel3 = createChannel(ChannelStatus.Opening)
      val expiresAt3 = Instant.now
      val createdAt3 = expiresAt.minusSeconds(80000)
      repository.updateChannelPoint(channel3.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel3.channelId, createdAt3, expiresAt3)
      repository.updateChannelStatus(channel3.channelId, ChannelStatus.Closed)

      val expected = LndChannel(
        channelUpdated.channelId,
        Currency.BTC,
        channelUpdated.fundingTransaction.value,
        channelUpdated.outputIndex.value,
        lifeTimeSeconds = 64000000L,
        channelStatus = channelUpdated.status
      )
      val result = repository.getExpiredChannels(Currency.BTC)
      result must be(List(expected))
    }

    "get an empty list when not exist expired channels" in {
      val channel = createChannel(ChannelStatus.Opening)
      val expiresAt = Instant.now
      val createdAt = expiresAt.minusSeconds(50000)
      repository.updateChannelPoint(channel.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel.channelId, createdAt, expiresAt)
      val result = repository.getExpiredChannels(Currency.LTC)
      result must be(empty)
    }
  }

  "getPendingChannels" should {
    "get a list of pending channels" in {
      val outpoint = Helpers.randomOutpoint()
      val channel = createChannel(ChannelStatus.Opening).withChannelPoint(outpoint)
      val expiresAt = Instant.now
      repository.updateChannelPoint(channel.channelId, outpoint)
      // Add an active channel
      val channel2 = createChannel(ChannelStatus.Opening).withChannelPoint(outpoint)
      val expiresAt2 = Instant.now.plusSeconds(60000)
      val createdAt2 = expiresAt.minusSeconds(120000)
      repository.updateChannelPoint(channel2.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel2.channelId, createdAt2, expiresAt2)
      // Add a closed  channel
      val channel3 = createChannel(ChannelStatus.Opening).withChannelPoint(outpoint)
      val expiresAt3 = Instant.now
      val createdAt3 = expiresAt.minusSeconds(80000)
      repository.updateChannelPoint(channel3.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel3.channelId, createdAt3, expiresAt3)
      repository.updateChannelStatus(channel3.channelId, ChannelStatus.Closed)

      val expected =
        LndChannel(
          channel.channelId,
          Currency.BTC,
          channel.fundingTransaction.value,
          channel.outputIndex.value,
          lifeTimeSeconds = 64000000L,
          channel.status
        )
      val result = repository.getProcessingChannels(Currency.BTC)
      result must be(List(expected))
    }

    "get an empty list when not exist expired channels" in {
      val channel = createChannel(ChannelStatus.Opening)
      val expiresAt = Instant.now
      val createdAt = expiresAt.minusSeconds(50000)
      repository.updateChannelPoint(channel.channelId, Helpers.randomOutpoint())
      repository.updateActiveChannel(channel.channelId, createdAt, expiresAt)
      val result = repository.getProcessingChannels(Currency.LTC)
      result must be(empty)
    }
  }

  "requestRentedChannelExtension(lnd)" should {
    "create request" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      succeed
    }

    "fail when extension request already exist" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)
      val error = intercept[PSQLException] {
        repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)
      }

      error.getMessage mustBe s"Extension request for ${channel.paymentRHash} in $payingCurrency already exist"
    }

    "fail when channel does not exist" in {
      val channel = SampleChannels.newChannel()
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      val error = intercept[PSQLException] {
        repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)
      }

      error.getMessage mustBe s"Channel ${channel.channelId} was not found"
    }
  }

  "requestRentedChannelExtension(connext)" should {
    "create request" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      succeed
    }

    "fail when extension request already exist" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)
      val error = intercept[PSQLException] {
        repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)
      }

      error.getMessage mustBe s"Extension request for ${channel.paymentRHash} in $payingCurrency already exist"
    }

    "fail when channel does not exist" in {
      val channel = SampleChannels.newConnextChannel()
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      val error = intercept[PSQLException] {
        repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)
      }

      error.getMessage mustBe s"Channel ${channel.channelId} was not found"
    }
  }

  "payRentedChannelExtensionFee(lnd)" should {
    "create payment" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payRentedChannelExtensionFee(extension)

      succeed
    }

    "fail when creating same payment twice" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payRentedChannelExtensionFee(extension)

      val error = intercept[PSQLException] {
        repository.payRentedChannelExtensionFee(extension)
      }

      error.getMessage mustBe s"Extension fee payment for ${channel.paymentRHash} in $payingCurrency already exist"
    }

    "fail when extension request does not exist" in {
      val extension = SampleChannels.newChannelExtension()

      val error = intercept[PSQLException] {
        repository.payRentedChannelExtensionFee(extension)
      }

      val expected = s"Extension fee request for ${extension.paymentHash} in ${extension.payingCurrency} was not found"
      error.getMessage mustBe expected
    }

    "update channel expiration date" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L
      val createdAt = Instant.now()
      val expiresAt = createdAt.plusSeconds(123456)

      repository.updateActiveChannel(channel.channelId, createdAt, expiresAt)

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payRentedChannelExtensionFee(extension)

      val extendedChannel = repository.findChannel(channel.channelId).value

      extendedChannel.expiresAt mustBe Some(expiresAt.plusSeconds(seconds))
    }

    "fail when channel has no expiration date" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      val result = repository.payRentedChannelExtensionFee(extension)

      result mustBe Left(s"Channel ${channel.channelId} has no expiration date")
    }
  }

  "payRentedChannelExtensionFee(connext)" should {
    "create payment" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findConnextChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payConnextRentedChannelExtensionFee(extension)

      succeed
    }

    "fail when creating same payment twice" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findConnextChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payConnextRentedChannelExtensionFee(extension)

      val error = intercept[PSQLException] {
        repository.payConnextRentedChannelExtensionFee(extension)
      }

      error.getMessage mustBe s"Extension fee payment for ${channel.paymentRHash} in $payingCurrency already exist"
    }

    "fail when extension request does not exist" in {
      val extension = SampleChannels.newChannelExtension()

      val error = intercept[PSQLException] {
        repository.payRentedChannelExtensionFee(extension)
      }

      val expected = s"Extension fee request for ${extension.paymentHash} in ${extension.payingCurrency} was not found"
      error.getMessage mustBe expected
    }

    "update channel expiration date" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findConnextChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payConnextRentedChannelExtensionFee(extension)

      val extendedChannel = repository.findConnextChannel(channel.channelId).value

      extendedChannel.expiresAt mustBe Some(channel.expiresAt.value.plusSeconds(seconds))
    }
  }

  "findChannelExtension" should {
    "find non paid channel extension" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val result = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      result.paymentHash mustBe channel.paymentRHash
      result.payingCurrency mustBe payingCurrency
      result.channelId mustBe channel.channelId
      result.fee mustBe fee
      result.seconds mustBe seconds
      result.paidAt mustBe None
    }

    "find paid channel extension" in {
      val channel = createChannel(ChannelStatus.Opening)
      val payingCurrency = Currency.XSN
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payRentedChannelExtensionFee(extension)

      val result = repository.findChannelExtension(channel.paymentRHash, payingCurrency).value
      result.paymentHash mustBe channel.paymentRHash
      result.payingCurrency mustBe payingCurrency
      result.channelId mustBe channel.channelId
      result.fee mustBe fee
      result.seconds mustBe seconds
      result.paidAt mustNot be(None)
    }

    "return None when channel extension does not exist" in {
      val channel = SampleChannels.newChannel()
      val payingCurrency = Currency.XSN

      val result = repository.findChannelExtension(channel.paymentRHash, payingCurrency)
      result mustBe None
    }
  }

  "findConnextChannelExtension" should {
    "find non paid channel extension" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val result = repository.findConnextChannelExtension(channel.paymentRHash, payingCurrency).value
      result.paymentHash mustBe channel.paymentRHash
      result.payingCurrency mustBe payingCurrency
      result.channelId mustBe channel.channelId
      result.fee mustBe fee
      result.seconds mustBe seconds
      result.paidAt mustBe None
    }

    "find paid channel extension" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)
      val payingCurrency = Currency.USDC
      val fee = Satoshis.One
      val seconds = 100L

      repository.requestRentedChannelExtension(channel.paymentRHash, payingCurrency, channel.channelId, fee, seconds)

      val extension = repository.findConnextChannelExtension(channel.paymentRHash, payingCurrency).value
      repository.payConnextRentedChannelExtensionFee(extension)

      val result = repository.findConnextChannelExtension(channel.paymentRHash, payingCurrency).value
      result.paymentHash mustBe channel.paymentRHash
      result.payingCurrency mustBe payingCurrency
      result.channelId mustBe channel.channelId
      result.fee mustBe fee
      result.seconds mustBe seconds
      result.paidAt mustNot be(None)
    }

    "return None when channel extension does not exist" in {
      val channel = SampleChannels.newConnextChannel()
      val payingCurrency = Currency.USDC

      val result = repository.findChannelExtension(channel.paymentRHash, payingCurrency)
      result mustBe None
    }
  }

  "findChannel by outpoint" should {
    "find channel" in {
      val fundingTxid = "d3f5712645c137c899ace9c8735a1030db5563d63365e1ee40730444eb537b8e"
      val channel = createChannel(ChannelStatus.Opening).copy(
        fundingTransaction = LndTxid.untrusted(fundingTxid),
        outputIndex = Some(0)
      )
      val outpoint = LndOutpoint(channel.fundingTransaction.value, channel.outputIndex.value)

      repository.updateChannelPoint(channel.channelId, outpoint)

      val result = repository.findChannel(channel.channelId).value
      result mustBe channel
    }

    "return None when channel does not exist" in {
      val fundingTxid = "d3f5712645c137c899ace9c8735a1030db5563d63365e1ee40730444eb537b8e"
      val channel = SampleChannels
        .newChannel()
        .copy(
          fundingTransaction = LndTxid.untrusted(fundingTxid),
          outputIndex = Some(0)
        )
      val outpoint = LndOutpoint(channel.fundingTransaction.value, channel.outputIndex.value)

      val result = repository.findChannel(outpoint)
      result mustBe None
    }
  }

  "findChannel by paymentHash" should {
    "get a channel" in {
      val channel = createChannel(ChannelStatus.Opening)

      val response = repository.findChannel(channel.paymentRHash, channel.payingCurrency)

      response.value must be(channel)
    }

    "fail when the channel does not exist" in {
      val channel = createChannel(ChannelStatus.Opening)

      val response = repository.findChannel(Helpers.randomPaymentHash(), channel.payingCurrency)

      response must be(empty)
    }
  }

  "findConnextChannel by paymentHash" should {
    "get a channel" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)

      val response = repository.findConnextChannel(channel.paymentRHash, channel.payingCurrency)

      response.value must be(channel)
    }

    "fail when the channel does not exist" in {
      val channel = createConnextChannel(ConnextChannelStatus.Active)

      val response = repository.findConnextChannel(Helpers.randomPaymentHash(), channel.payingCurrency)

      response must be(empty)
    }
  }

  "updateClosedChannel" should {
    "update the channel to closed" in {
      val outpoint = Helpers.randomOutpoint()
      val channel = createChannel(ChannelStatus.Active).withChannelPoint(outpoint)
      val reason = "expired"
      val closedBy = "REMOTE"

      repository.updateChannelPoint(channel.channelId, outpoint)
      repository.updateClosedChannel(outpoint, reason, closedBy)

      val result = repository.findChannel(channel.channelId).value

      result.status mustBe ChannelStatus.Closed
      result.closingType mustBe Some(reason)
      result.closedBy mustBe Some(closedBy)
      result.closingType.isDefined mustBe true
    }

    "Fail when the channel id does not exist" in {
      val outpoint = Helpers.randomOutpoint()
      val reason = "expired"
      val closedBy = "REMOTE"

      intercept[AssertionError] {
        repository.updateClosedChannel(outpoint, reason, closedBy)
      }
    }
  }

  "createCloseExpiredChannelRequest" should {
    "create a new close expired channel request" in {
      val channel = createChannel(ChannelStatus.Opening)

      repository.createCloseExpiredChannelRequest(channel.channelId, false, Instant.now())

      succeed
    }

    "Fail when trying to create a repeated request" in {
      val channel = createChannel(ChannelStatus.Opening)

      repository.createCloseExpiredChannelRequest(channel.channelId, false, Instant.now())
      val ex = intercept[PSQLException] {
        repository.createCloseExpiredChannelRequest(channel.channelId, false, Instant.now())
      }

      ex.getMessage must be(s"close expired channel request for ${channel.channelId} already exist")
    }

    "Fail when trying to create a request for a channel that does not exist" in {
      val channel = SampleChannels.newChannel()

      val ex = intercept[PSQLException] {
        repository.createCloseExpiredChannelRequest(channel.channelId, false, Instant.now())
      }

      ex.getMessage must be(s"channel ${channel.channelId} not found")
    }
  }

  "setActive" should {
    "update the channel's status" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      repository.setActive(channel2.channelAddress.value)

      val result1 = repository.findConnextChannel(channel1.paymentRHash, channel1.payingCurrency).value.status
      result1 mustBe ConnextChannelStatus.Confirming

      val result2 = repository.findConnextChannel(channel2.paymentRHash, channel2.payingCurrency).value.status
      result2 mustBe ConnextChannelStatus.Active
    }

    "fail when channel does not exists" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      val address = Helpers.randomChannelAddress()
      val error = intercept[AssertionError] {
        repository.setActive(address)
      }

      error.getMessage mustBe s"assertion failed: The channel status wasn't updated, likely due to a race condition, channelAddress = $address, channelStatus = Active"

      val result1 = repository.findConnextChannel(channel1.paymentRHash, channel1.payingCurrency).value.status
      result1 mustBe ConnextChannelStatus.Confirming

      val result2 = repository.findConnextChannel(channel2.paymentRHash, channel2.payingCurrency).value.status
      result2 mustBe ConnextChannelStatus.Confirming
    }
  }

  "setClosed" should {
    "update the channel's status" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      repository.setClosed(channel2.channelAddress.value)

      val result1 = repository.findConnextChannel(channel1.paymentRHash, channel1.payingCurrency).value.status
      result1 mustBe ConnextChannelStatus.Confirming

      val result2 = repository.findConnextChannel(channel2.paymentRHash, channel2.payingCurrency).value.status
      result2 mustBe ConnextChannelStatus.Closed
    }

    "fail when channel does not exists" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      val address = Helpers.randomChannelAddress()
      val error = intercept[AssertionError] {
        repository.setClosed(address)
      }

      error.getMessage mustBe s"assertion failed: The channel status wasn't updated, likely due to a race condition, channelAddress = $address, channelStatus = Closed"

      val result1 = repository.findConnextChannel(channel1.paymentRHash, channel1.payingCurrency).value.status
      result1 mustBe ConnextChannelStatus.Confirming

      val result2 = repository.findConnextChannel(channel2.paymentRHash, channel2.payingCurrency).value.status
      result2 mustBe ConnextChannelStatus.Confirming
    }
  }

  "findConnextConfirmingChannels" should {
    "find only pending channels" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Active
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          status = ConnextChannelStatus.Confirming
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      val result = repository.findConnextConfirmingChannels()
      result mustBe List(ConnextChannel(channel2.channelAddress.value, transactionHash2, channelFeePayment2.currency))
    }
  }

  "getConnextExpiredChannels" should {
    "find expired channels" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          publicIdentifier = clientPublicIdentifier.identifier,
          status = ConnextChannelStatus.Active,
          expiresAt = Some(Instant.now().minusSeconds(1.hour.toSeconds))
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          publicIdentifier = clientPublicIdentifier.identifier,
          status = ConnextChannelStatus.Active,
          expiresAt = Some(Instant.now().plusSeconds(1.hour.toSeconds))
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      val result = repository.getConnextExpiredChannels()
      result mustBe List(channel1)
    }

    "return empty list when there are no expired channels" in {
      val clientPublicIdentifier = createClientPublicIdentifier(
        ConnextPublicIdentifier("vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"),
        Currency.WETH
      )

      val transactionHash1 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
      val channelFeePayment1 = SampleChannels.newChannelFeePayment()
      val channel1 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment1.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          publicIdentifier = clientPublicIdentifier.identifier,
          status = ConnextChannelStatus.Active,
          expiresAt = Some(Instant.now().plusSeconds(1.hour.toSeconds))
        )

      val transactionHash2 = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e65"
      val channelFeePayment2 = SampleChannels.newChannelFeePayment()
      val channel2 = SampleChannels
        .newConnextChannel()
        .copy(
          payingCurrency = channelFeePayment2.payingCurrency,
          clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId,
          publicIdentifier = clientPublicIdentifier.identifier,
          status = ConnextChannelStatus.Active,
          expiresAt = Some(Instant.now().plusSeconds(1.hour.toSeconds))
        )

      repository.createChannelFeePayment(channelFeePayment1, channel1.paymentRHash, Satoshis.One)
      repository.createChannel(channel1, transactionHash1)

      repository.createChannelFeePayment(channelFeePayment2, channel2.paymentRHash, Satoshis.One)
      repository.createChannel(channel2, transactionHash2)

      val result = repository.getConnextExpiredChannels()
      result mustBe List.empty
    }
  }

  "createConnextChannelContractDeploymentFee" should {
    "create a new connext channel contract deployment fee" in {
      val identifier = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier = createClientPublicIdentifier(identifier, Currency.ETH)

      repository.createConnextChannelContractDeploymentFee("hash", clientPublicIdentifier.clientId, Satoshis.One)

      succeed
    }

    "fail when trying to create a repeated connext channel contract deployment fee" in {
      val identifier1 = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier1 = createClientPublicIdentifier(identifier1, Currency.ETH)
      repository.createConnextChannelContractDeploymentFee("hash", clientPublicIdentifier1.clientId, Satoshis.One)

      val ex = intercept[PSQLException] {
        val identifier2 = Helpers.randomPublicIdentifier()
        val clientPublicIdentifier2 = createClientPublicIdentifier(identifier2, Currency.ETH)
        repository.createConnextChannelContractDeploymentFee("hash", clientPublicIdentifier2.clientId, Satoshis.One)
      }

      ex.getMessage must be("transaction hash already registered")
    }

    "fail when trying to create another connext channel contract deployment fee for the same client" in {
      val identifier = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier = createClientPublicIdentifier(identifier, Currency.ETH)
      repository.createConnextChannelContractDeploymentFee("hash", clientPublicIdentifier.clientId, Satoshis.One)

      val ex = intercept[PSQLException] {
        repository.createConnextChannelContractDeploymentFee("hash2", clientPublicIdentifier.clientId, Satoshis.One)
      }

      ex.getMessage must be(s"client ${clientPublicIdentifier.clientId} has already paid the fee")
    }

    "fail when trying to create a connext channel contract deployment fee for a client that does not exist" in {
      val clientId = ClientId.random()

      val ex = intercept[PSQLException] {
        repository.createConnextChannelContractDeploymentFee("hash", clientId, Satoshis.One)
      }

      ex.getMessage must be(s"client $clientId not found")
    }
  }

  "findConnextChannelContractDeploymentFee" should {
    "find the fee" in {
      val identifier1 = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier1 = createClientPublicIdentifier(identifier1, Currency.ETH)
      repository.createConnextChannelContractDeploymentFee("hash1", clientPublicIdentifier1.clientId, Satoshis.One)

      val identifier2 = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier2 = createClientPublicIdentifier(identifier2, Currency.ETH)
      repository.createConnextChannelContractDeploymentFee("hash2", clientPublicIdentifier2.clientId, Satoshis.One)

      val response = repository.findConnextChannelContractDeploymentFee(clientPublicIdentifier2.clientId)

      response.value.transactionHash must be("hash2")
      response.value.clientId must be(clientPublicIdentifier2.clientId)
    }

    "return None when client has no fee registered" in {
      val identifier1 = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier1 = createClientPublicIdentifier(identifier1, Currency.ETH)
      repository.createConnextChannelContractDeploymentFee("hash1", clientPublicIdentifier1.clientId, Satoshis.One)

      val identifier2 = Helpers.randomPublicIdentifier()
      val clientPublicIdentifier2 = createClientPublicIdentifier(identifier2, Currency.ETH)
      repository.createConnextChannelContractDeploymentFee("hash2", clientPublicIdentifier2.clientId, Satoshis.One)

      val response = repository.findConnextChannelContractDeploymentFee(ClientId.random())

      response must be(empty)
    }
  }

  private def createChannel(status: ChannelStatus) = {
    val channelFeePayment = SampleChannels.newChannelFeePayment()
    val publicKey = Helpers.randomPublicKey()
    val clientPublicKey = createClientPublicKey(publicKey, Currency.XSN)
    val channel = SampleChannels
      .newChannel()
      .copy(
        status = status,
        payingCurrency = channelFeePayment.payingCurrency,
        clientPublicKeyId = clientPublicKey.clientPublicKeyId
      )

    repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
    repository.createChannel(channel)

    channel
  }

  private def createConnextChannel(status: ConnextChannelStatus) = {
    val transactionHash = "0xbaee42943317e81dfd8a20227b8726563dd6fb4318f893f94a1f28b4cfb48e60"
    val channelFeePayment = SampleChannels.newChannelFeePayment()
    val publicIdentifier = Helpers.randomPublicIdentifier()
    val clientPublicIdentifier = createClientPublicIdentifier(publicIdentifier, Currency.XSN)
    val channel = SampleChannels
      .newConnextChannel()
      .copy(
        status = status,
        payingCurrency = channelFeePayment.payingCurrency,
        publicIdentifier = publicIdentifier,
        clientPublicIdentifierId = clientPublicIdentifier.clientPublicIdentifierId
      )

    repository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
    repository.createChannel(channel, transactionHash)

    channel
  }

  private def createClientPublicKey(publicKey: Identifier.LndPublicKey, currency: Currency): ClientLndPublicKey = {
    val clientsRepository = new ClientsPostgresRepository(database)
    val walletId = Helpers.randomWalletId()

    val clientId = clientsRepository.createWalletClient(walletId)
    clientsRepository.registerPublicKey(clientId, publicKey, currency)

    clientsRepository.findPublicKey(clientId, currency).value
  }

  private def createClientPublicIdentifier(
      identifier: ConnextPublicIdentifier,
      currency: Currency
  ): ClientConnextPublicIdentifier = {
    val clientsRepository = new ClientsPostgresRepository(database)
    val walletId = Helpers.randomWalletId()

    val clientId = clientsRepository.createWalletClient(walletId)
    clientsRepository.registerPublicIdentifier(clientId, identifier, currency)

    clientsRepository.findPublicIdentifier(clientId, currency).value
  }
}
