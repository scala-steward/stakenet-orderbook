package io.stakenet.orderbook.repositories.clients

import java.time.Instant
import java.util.UUID

import helpers.Helpers
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.clients.ClientIdentifier.ClientLndPublicKey
import io.stakenet.orderbook.models.lnd.ChannelStatus
import io.stakenet.orderbook.models.{Channel, Currency, Satoshis}
import io.stakenet.orderbook.repositories.channels.ChannelsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

import scala.concurrent.duration._

class ClientsRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new ClientsPostgresRepository(database)

  "createWalletClient" should {
    "create wallet client" in {
      val walletId = Helpers.randomWalletId()

      repository.createWalletClient(walletId)

      succeed
    }

    "fail on duplicated wallet client" in {
      val walletId = Helpers.randomWalletId()

      repository.createWalletClient(walletId)
      val error = intercept[PSQLException] {
        repository.createWalletClient(walletId)
      }

      error.getMessage mustBe s"wallet client $walletId already exist"
    }
  }

  "findBotMakerClient " should {
    "find bot maker client" in {
      val walletId = Helpers.randomWalletId()
      repository.createWalletClient(walletId)

      val client = repository.findBotMakerClient("vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD")

      val clientID = ClientId(UUID.fromString("11b4e5a8-68a0-4bdf-b11e-e52d39804fbe"))
      client mustBe Some(BotMakerClient("bot1.xsnbot.com", clientID, false))
    }

    "return None when bot maker client does not exist" in {
      val walletId = Helpers.randomWalletId()
      repository.createWalletClient(walletId)

      val client = repository.findBotMakerClient("nope")

      client mustBe None
    }
  }

  "findWalletClient " should {
    "find wallet client" in {
      val walletId = Helpers.randomWalletId()
      val walletUserId = repository.createWalletClient(walletId)

      val client = repository.findWalletClient(walletId)

      client mustBe Some(WalletClient(walletId, walletUserId))
    }

    "return None when wallet client does not exist" in {
      val walletId = Helpers.randomWalletId()
      repository.createWalletClient(walletId)

      val client = repository.findWalletClient(Helpers.randomWalletId())

      client mustBe None
    }
  }

  "registerPublicKey" should {
    "register a public key" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicKey = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId, publicKey, Currency.XSN)

      succeed
    }

    "register the same key for different currencies" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicKey = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId, publicKey, Currency.XSN)
      repository.registerPublicKey(clientId, publicKey, Currency.BTC)

      succeed
    }

    "fail when the same key and currency was already registered" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicKey = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId, publicKey, Currency.XSN)

      val error = intercept[PSQLException] {
        repository.registerPublicKey(clientId, publicKey, Currency.XSN)
      }

      error.getMessage mustBe s"$publicKey for XSN already registered"
    }

    "fail when registering multiple keys for the same currency" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicKey1 = Helpers.randomPublicKey()
      val publicKey2 = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId, publicKey1, Currency.XSN)
      val error = intercept[PSQLException] {
        repository.registerPublicKey(clientId, publicKey2, Currency.XSN)
      }

      error.getMessage mustBe s"$clientId already has a key for XSN registered"
    }

    "fail when user registering the key does not exist" in {
      val clientId = ClientId.random()
      val publicKey = Helpers.randomPublicKey()

      val error = intercept[PSQLException] {
        repository.registerPublicKey(clientId, publicKey, Currency.XSN)
      }

      error.getMessage mustBe s"client $clientId does not exist"
    }

    "findPublicKey" should {
      "find the public key if it exists" in {
        val walletId = Helpers.randomWalletId()
        val clientId = repository.createWalletClient(walletId)
        val publicKey1 = Helpers.randomPublicKey()
        val publicKey2 = Helpers.randomPublicKey()

        repository.registerPublicKey(clientId, publicKey1, Currency.XSN)
        repository.registerPublicKey(clientId, publicKey2, Currency.BTC)

        val clientPublicKey = repository.findPublicKey(clientId, Currency.BTC)

        clientPublicKey.value.key mustBe publicKey2
      }

      "return None when public key does not exists" in {
        val walletId = Helpers.randomWalletId()
        val clientId = repository.createWalletClient(walletId)
        val publicKey1 = Helpers.randomPublicKey()
        val publicKey2 = Helpers.randomPublicKey()

        repository.registerPublicKey(clientId, publicKey1, Currency.XSN)
        repository.registerPublicKey(clientId, publicKey2, Currency.BTC)

        val publicKey = repository.findPublicKey(clientId, Currency.LTC)

        publicKey mustBe None
      }
    }
  }

  "registerPublicIdentifier" should {
    "register a public identifier" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicIdentifier = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId, publicIdentifier, Currency.XSN)

      succeed
    }

    "register the same identifier for different currencies" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicIdentifier = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId, publicIdentifier, Currency.XSN)
      repository.registerPublicIdentifier(clientId, publicIdentifier, Currency.BTC)

      succeed
    }

    "fail when the same identifier and currency was already registered" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicIdentifier = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId, publicIdentifier, Currency.XSN)

      val error = intercept[PSQLException] {
        repository.registerPublicIdentifier(clientId, publicIdentifier, Currency.XSN)
      }

      error.getMessage mustBe s"$publicIdentifier for XSN already registered"
    }

    "fail when registering multiple identifiers for the same currency" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicIdentifier1 = Helpers.randomPublicIdentifier()
      val publicIdentifier2 = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId, publicIdentifier1, Currency.XSN)
      val error = intercept[PSQLException] {
        repository.registerPublicIdentifier(clientId, publicIdentifier2, Currency.XSN)
      }

      error.getMessage mustBe s"$clientId already has a public identifier for XSN registered"
    }

    "fail when user registering the key does not exist" in {
      val clientId = ClientId.random()
      val publicIdentifier = Helpers.randomPublicIdentifier()

      val error = intercept[PSQLException] {
        repository.registerPublicIdentifier(clientId, publicIdentifier, Currency.XSN)
      }

      error.getMessage mustBe s"client $clientId does not exist"
    }
  }

  "findPublicIdentifier" should {
    "find the public identifier if it exists" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicIdentifier1 = Helpers.randomPublicIdentifier()
      val publicIdentifier2 = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId, publicIdentifier1, Currency.XSN)
      repository.registerPublicIdentifier(clientId, publicIdentifier2, Currency.BTC)

      val clientPublicIdentifier = repository.findPublicIdentifier(clientId, Currency.BTC)

      clientPublicIdentifier.value.identifier mustBe publicIdentifier2
    }

    "return None when public key does not exists" in {
      val walletId = Helpers.randomWalletId()
      val clientId = repository.createWalletClient(walletId)
      val publicIdentifier1 = Helpers.randomPublicIdentifier()
      val publicIdentifier2 = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId, publicIdentifier1, Currency.XSN)
      repository.registerPublicIdentifier(clientId, publicIdentifier2, Currency.BTC)

      val publicIdentifier = repository.findPublicIdentifier(clientId, Currency.LTC)

      publicIdentifier mustBe None
    }
  }

  "getAllClientIds" should {
    "get a list of ClientIds" in {
      val walletClientIds = List(
        repository.createWalletClient(Helpers.randomWalletId()),
        repository.createWalletClient(Helpers.randomWalletId()),
        repository.createWalletClient(Helpers.randomWalletId())
      )

      val botMakersIds = List(
        ClientId(UUID.fromString("11b4e5a8-68a0-4bdf-b11e-e52d39804fbe")),
        ClientId(UUID.fromString("e354b922-b537-432f-881c-10cb29e303df")),
        ClientId(UUID.fromString("32f208d1-206c-4b75-8e34-a0f6fbd59db9")),
        ClientId(UUID.fromString("d361540d-b5bf-4ac3-93e8-693d0f8fe6bd")),
        ClientId(UUID.fromString("a1f4c407-836e-4b8d-8d1d-6668287c5d32")),
        ClientId(UUID.fromString("ab5732dd-0d23-4571-ba20-5e5f5cc44b3a")),
        ClientId(UUID.fromString("d6317814-6092-487f-b005-0917644499da")),
        ClientId(UUID.fromString("c247face-9388-4871-a897-6187de9a850f")),
        ClientId(UUID.fromString("260e3692-0e2d-48a5-aded-5c5184ab0864")),
        ClientId(UUID.fromString("c04a34c8-24e5-4315-9442-2ae093e0a0aa")),
        ClientId(UUID.fromString("52a27dcb-9417-4fbe-b564-50eaa8732280")),
        ClientId(UUID.fromString("b4ee774a-a14d-4c5c-ba02-c6e23cf3f525")),
        ClientId(UUID.fromString("382338c5-842e-4475-a753-018741781ccb")),
        ClientId(UUID.fromString("47feccb5-d083-4aa3-9e62-ed1c445fee94")),
        ClientId(UUID.fromString("be4d165f-1bd7-4ff5-89f5-140f304d2203")),
        ClientId(UUID.fromString("07c1b66e-6e33-48e0-b44b-0a4ce0413014")),
        ClientId(UUID.fromString("a982737f-5140-40fb-aea7-1e0306f420ee"))
      )

      val result = repository.getAllClientIds()

      implicit val ordering: Ordering[ClientId] = Ordering.by(id => id.toString)

      result.sorted mustBe (walletClientIds ++ botMakersIds).sorted
    }
  }

  "getAllClientPublicKeys" should {
    "get all the client's public keys" in {
      val clientId1 = repository.createWalletClient(Helpers.randomWalletId())
      val clientId2 = repository.createWalletClient(Helpers.randomWalletId())
      val xsnKey1 = Helpers.randomPublicKey()
      val xsnKey2 = Helpers.randomPublicKey()
      val btcKey = Helpers.randomPublicKey()
      val ltcKey = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId1, xsnKey1, Currency.XSN)
      repository.registerPublicKey(clientId1, btcKey, Currency.BTC)
      repository.registerPublicKey(clientId2, xsnKey2, Currency.XSN)
      repository.registerPublicKey(clientId2, ltcKey, Currency.LTC)

      val result1 = repository.getAllClientPublicKeys(clientId1)
      val result2 = repository.getAllClientPublicKeys(clientId2)

      result1.length mustBe 2
      result1.exists(r => r.key == xsnKey1 && r.currency == Currency.XSN) mustBe true
      result1.exists(r => r.key == btcKey && r.currency == Currency.BTC) mustBe true

      result2.length mustBe 2
      result2.exists(r => r.key == xsnKey2 && r.currency == Currency.XSN) mustBe true
      result2.exists(r => r.key == ltcKey && r.currency == Currency.LTC) mustBe true
    }

    "get an empty list when client has no public keys" in {
      val clientId = repository.createWalletClient(Helpers.randomWalletId())

      val result = repository.getAllClientPublicKeys(clientId)

      result.isEmpty mustBe true
    }

    "get an empty list when client does not exist" in {
      val result = repository.getAllClientPublicKeys(ClientId.random())

      result.isEmpty mustBe true
    }
  }

  "getPublicKeyRentedCapacity" should {
    "get the rented capacity for a client public key" in {
      val clientId = repository.createWalletClient(Helpers.randomWalletId())

      repository.registerPublicKey(clientId, Helpers.randomPublicKey(), Currency.XSN)
      repository.registerPublicKey(clientId, Helpers.randomPublicKey(), Currency.BTC)

      val keys = repository.getAllClientPublicKeys(clientId)
      val xsnKey = keys.find(_.currency == Currency.XSN).value
      val btcKey = keys.find(_.currency == Currency.BTC).value
      val now = Instant.now

      createChannel(
        clientPublicKey = xsnKey,
        capacity = Satoshis.from(BigDecimal(1)).value,
        expirationDate = now.plusSeconds(1.day.toSeconds)
      )

      createChannel(
        clientPublicKey = xsnKey,
        capacity = Satoshis.from(BigDecimal(2)).value,
        expirationDate = now.plusSeconds(1.day.toSeconds)
      )

      createChannel(
        clientPublicKey = xsnKey,
        capacity = Satoshis.from(BigDecimal(5)).value,
        expirationDate = now.minusSeconds(1.day.toSeconds)
      )

      createChannel(
        clientPublicKey = btcKey,
        capacity = Satoshis.from(BigDecimal(4)).value,
        expirationDate = now.plusSeconds(1.day.toSeconds)
      )

      val result = repository.getPublicKeyRentedCapacity(xsnKey)
      result mustBe Satoshis.from(BigDecimal(3)).value
    }

    "get zero when client has no rented channels" in {
      val clientId = repository.createWalletClient(Helpers.randomWalletId())

      repository.registerPublicKey(clientId, Helpers.randomPublicKey(), Currency.XSN)

      val clientPublicKey = repository.getAllClientPublicKeys(clientId).head
      val result = repository.getPublicKeyRentedCapacity(clientPublicKey)

      result mustBe Satoshis.Zero
    }
  }

  "logClientStatus" should {
    "log client's status" in {
      val clientId = repository.createWalletClient(Helpers.randomWalletId())

      repository.logClientInfo(clientId, 100, 50, Instant.now)

      succeed
    }

    "fail when client does not exist" in {
      val clientId = ClientId.random()
      val error = intercept[PSQLException] {
        repository.logClientInfo(clientId, 100, 50, Instant.now)
      }

      error.getMessage mustBe s"client $clientId not found"
    }
  }

  "findAllLndClientPublicKeys" should {
    "get all the client's public keys" in {
      val clientId1 = repository.createWalletClient(Helpers.randomWalletId())
      val clientId2 = repository.createWalletClient(Helpers.randomWalletId())
      val xsnKey1 = Helpers.randomPublicKey()
      val xsnKey2 = Helpers.randomPublicKey()
      val btcKey = Helpers.randomPublicKey()
      val ltcKey = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId1, xsnKey1, Currency.XSN)
      repository.registerPublicKey(clientId1, btcKey, Currency.BTC)
      repository.registerPublicKey(clientId2, xsnKey2, Currency.XSN)
      repository.registerPublicKey(clientId2, ltcKey, Currency.LTC)

      val result1 = repository.findAllLndClientPublicKeys(Currency.XSN)
      val result2 = repository.findAllLndClientPublicKeys(Currency.BTC)

      result1.length mustBe 2
      result1.exists(r => r.key == xsnKey1 && r.currency == Currency.XSN) mustBe true
      result1.exists(r => r.key == xsnKey2 && r.currency == Currency.XSN) mustBe true

      result2.length mustBe 1
      result2.exists(r => r.key == btcKey && r.currency == Currency.BTC) mustBe true
    }

    "get an empty list when currency has no public keys" in {
      val clientId1 = repository.createWalletClient(Helpers.randomWalletId())
      val clientId2 = repository.createWalletClient(Helpers.randomWalletId())
      val xsnKey1 = Helpers.randomPublicKey()
      val xsnKey2 = Helpers.randomPublicKey()
      val btcKey = Helpers.randomPublicKey()

      repository.registerPublicKey(clientId1, xsnKey1, Currency.XSN)
      repository.registerPublicKey(clientId1, btcKey, Currency.BTC)
      repository.registerPublicKey(clientId2, xsnKey2, Currency.XSN)

      val result = repository.findAllLndClientPublicKeys(Currency.LTC)
      result mustBe empty
    }
  }

  "findAllConnextClientPublicIdentifiers" should {
    "get all the client's public identifiers" in {
      val clientId1 = repository.createWalletClient(Helpers.randomWalletId())
      val clientId2 = repository.createWalletClient(Helpers.randomWalletId())
      val usdtKey1 = Helpers.randomPublicIdentifier()
      val usdtKey2 = Helpers.randomPublicIdentifier()
      val wethKey = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId1, usdtKey1, Currency.USDT)
      repository.registerPublicIdentifier(clientId2, usdtKey2, Currency.USDT)
      repository.registerPublicIdentifier(clientId1, wethKey, Currency.WETH)

      val result1 = repository.findAllConnextClientPublicIdentifiers(Currency.USDT)
      val result2 = repository.findAllConnextClientPublicIdentifiers(Currency.WETH)

      result1.length mustBe 2
      result1.exists(r => r.identifier == usdtKey1 && r.currency == Currency.USDT) mustBe true
      result1.exists(r => r.identifier == usdtKey2 && r.currency == Currency.USDT) mustBe true

      result2.length mustBe 1
      result2.exists(r => r.identifier == wethKey && r.currency == Currency.WETH) mustBe true
    }

    "get an empty list when currency has no public identifiers" in {
      val clientId1 = repository.createWalletClient(Helpers.randomWalletId())
      val clientId2 = repository.createWalletClient(Helpers.randomWalletId())
      val usdtKey1 = Helpers.randomPublicIdentifier()
      val usdtKey2 = Helpers.randomPublicIdentifier()

      repository.registerPublicIdentifier(clientId1, usdtKey1, Currency.USDT)
      repository.registerPublicIdentifier(clientId2, usdtKey2, Currency.USDT)

      val result = repository.findAllConnextClientPublicIdentifiers(Currency.WETH)
      result mustBe empty
    }
  }

  private def createChannel(clientPublicKey: ClientLndPublicKey, capacity: Satoshis, expirationDate: Instant): Unit = {
    val channel = Channel.LndChannel.from(
      Helpers.randomPaymentHash(),
      Currency.BTC,
      clientPublicKey.key,
      clientPublicKey.clientPublicKeyId,
      ChannelStatus.Active
    )

    val channelFeePayment = SampleChannels
      .newChannelFeePayment()
      .copy(currency = clientPublicKey.currency, payingCurrency = channel.payingCurrency, capacity = capacity)

    val channelsRepository = new ChannelsPostgresRepository(database)
    channelsRepository.createChannelFeePayment(channelFeePayment, channel.paymentRHash, Satoshis.One)
    channelsRepository.createChannel(channel)
    channelsRepository.updateActiveChannel(channel.channelId, Instant.now, expirationDate)

    ()
  }
}
