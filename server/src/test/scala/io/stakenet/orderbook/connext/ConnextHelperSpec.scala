package io.stakenet.orderbook.connext

import helpers.Helpers
import io.stakenet.orderbook.connext.ConnextHelper.ResolveTransferError.{
  CouldNotResolveTransfer,
  NoChannelWithCounterParty,
  TransferNotFound
}
import io.stakenet.orderbook.models.ChannelIdentifier.ConnextChannelAddress
import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.{Currency, Preimage, Satoshis}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{BodyWritable, WSClient, WSRequest}

import scala.concurrent.Future

class ConnextHelperSpec extends AsyncWordSpec with Matchers {
  private val host = "localhost"
  private val port = "8005"
  private val publicIdentifier = "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"
  private val chainId = "1337"

  "getAllChannels" should {
    "return a list of channel addresses" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val channelAddress1 = Helpers.randomChannelAddress()
      val channelAddress2 = Helpers.randomChannelAddress()
      val channelAddress3 = Helpers.randomChannelAddress()
      val channels = List(channelAddress1, channelAddress2, channelAddress3)

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels",
        jsonResponse = Json.parse(s"""["$channelAddress1", "$channelAddress2", "$channelAddress3"]""")
      )

      channels.foreach { channelAddress =>
        mockGetRequest(
          ws,
          url = s"$host:$port/$publicIdentifier/channels/$channelAddress",
          jsonResponse = getChannelResponse(channelAddress)
        )
      }

      connextHelper.getAllChannels(Currency.USDT).map { result =>
        result.map(_.channelAddress) mustBe channels
      }
    }
  }

  "resolveTransfer" should {
    "resolve a transfer" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val clientPublicIdentifier = Helpers.randomPublicIdentifier()
      val channelAddress = Helpers.randomChannelAddress()
      val amount = 123456789

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/counterparty/$clientPublicIdentifier/chain-id/$chainId",
        jsonResponse = getChannelResponse(channelAddress)
      )

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/$channelAddress/active-transfers",
        jsonResponse = getActiveTransfersResponse(
          paymentHash -> amount,
          PaymentRHash.from(Preimage.random()) -> 500000
        )
      )

      mockPostRequest(
        ws,
        url = s"$host:$port/transfers/resolve",
        jsonResponse = Json.parse("{}"),
        status = 200
      )

      connextHelper.resolveTransfer(Currency.WETH, clientPublicIdentifier, paymentHash, preimage).map {
        case Right(paymentData) =>
          paymentData.amount mustBe Currency.WETH.satoshis(amount).value

        case Left(error) =>
          fail(s"$error")
      }
    }

    "fail when /transfers/resolve returns an error" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val clientPublicIdentifier = Helpers.randomPublicIdentifier()
      val channelAddress = Helpers.randomChannelAddress()
      val amount = 123456789

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/counterparty/$clientPublicIdentifier/chain-id/$chainId",
        jsonResponse = getChannelResponse(channelAddress)
      )

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/$channelAddress/active-transfers",
        jsonResponse = getActiveTransfersResponse(
          paymentHash -> amount,
          PaymentRHash.from(Preimage.random()) -> 500000
        )
      )

      mockPostRequest(
        ws,
        url = s"$host:$port/transfers/resolve",
        jsonResponse = Json.parse("{}"),
        status = 500
      )

      connextHelper.resolveTransfer(Currency.WETH, clientPublicIdentifier, paymentHash, preimage).map {
        case Right(paymentData) =>
          fail(s"$paymentData")

        case Left(error) =>
          error mustBe CouldNotResolveTransfer(500, "")
      }
    }

    "fail when transfer is not found" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val clientPublicIdentifier = Helpers.randomPublicIdentifier()
      val channelAddress = Helpers.randomChannelAddress()

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/counterparty/$clientPublicIdentifier/chain-id/$chainId",
        jsonResponse = getChannelResponse(channelAddress)
      )

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/$channelAddress/active-transfers",
        jsonResponse = getActiveTransfersResponse(
          PaymentRHash.from(Preimage.random()) -> 500000
        )
      )

      connextHelper.resolveTransfer(Currency.WETH, clientPublicIdentifier, paymentHash, preimage).map {
        case Right(paymentData) =>
          fail(s"$paymentData")

        case Left(error) =>
          error mustBe TransferNotFound(channelAddress, paymentHash)
      }
    }

    "fail when there is no channel with the client" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val clientPublicIdentifier = Helpers.randomPublicIdentifier()

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/counterparty/$clientPublicIdentifier/chain-id/$chainId",
        jsonResponse = Json.parse("{}")
      )

      connextHelper.resolveTransfer(Currency.WETH, clientPublicIdentifier, paymentHash, preimage).map {
        case Right(paymentData) =>
          fail(s"$paymentData")

        case Left(error) =>
          error mustBe NoChannelWithCounterParty(clientPublicIdentifier)
      }
    }
  }

  "openChannel" should {
    "return the channel address" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val channelAddress = Helpers.randomChannelAddress()
      val publicIdentifier = Helpers.randomPublicIdentifier()

      mockPostRequest(
        ws,
        url = s"$host:$port/setup",
        jsonResponse = Json.parse(s"""{"channelAddress": "$channelAddress"}"""),
        status = 200
      )

      connextHelper.openChannel(publicIdentifier, Currency.USDT).map { result =>
        result mustBe channelAddress
      }
    }
  }

  "channelDeposit" should {
    "deposit on a channel" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val channelAddress = Helpers.randomChannelAddress()
      val amount = Helpers.asSatoshis("0.00000123")

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/$channelAddress",
        jsonResponse = getChannelResponse(channelAddress)
      )

      mockPostRequest(
        ws,
        url = s"$host:$port/send-deposit-tx",
        jsonResponse = Json.parse(
          "{\"txHash\": \"0xeaefa6d63268d0192bf3959fe93ad87cf314a55a0af4aaf47b8da39a53d45c66\"}"
        ),
        status = 200
      )

      mockPostRequest(
        ws,
        url = s"$host:$port/deposit",
        jsonResponse = Json.parse(s"""{"channelAddress": "$channelAddress"}"""),
        status = 200
      )

      connextHelper.channelDeposit(channelAddress, amount, Currency.USDT).map { _ =>
        succeed
      }
    }
  }

  "channelWithdrawal" should {
    "withdraw funds from a channel" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val channelAddress = Helpers.randomChannelAddress()
      val transactionHash = "0x6392bc42e3afda1d256cc8f2194be39dbfaa33a14bb38c02347d783e48eb3dbb"
      val amount = Helpers.asSatoshis("0.00000123")

      val response = Json.parse(
        s"""{
           |  "channelAddress": "$channelAddress",
           |  "transferId": "0x9b9fd7f68173c18da42f5acb7c3798c7b0f10b6be2766c68c84db057eeadd94f",
           |  "transactionHash": "$transactionHash"
           |}""".stripMargin
      )

      mockPostRequest(ws, url = s"$host:$port/withdraw", jsonResponse = response, status = 200)

      connextHelper.channelWithdrawal(channelAddress, amount, Currency.USDT).map { response =>
        response mustBe transactionHash
      }
    }
  }

  "getChannelLocalBalance" should {
    "get channel local balance" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val channelAddress = Helpers.randomChannelAddress()

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/$channelAddress",
        jsonResponse = getChannelResponse(channelAddress)
      )

      connextHelper.getChannelLocalBalance(channelAddress, Currency.USDT).map { response =>
        response mustBe Helpers.asSatoshis("50000.000000")
      }
    }

    "get 0 when hub has no balance on channel" in {
      val ws = mock[WSClient]
      val connextHelper = getConnextHelper(ws)
      val channelAddress = Helpers.randomChannelAddress()

      mockGetRequest(
        ws,
        url = s"$host:$port/$publicIdentifier/channels/$channelAddress",
        jsonResponse = getChannelResponse(channelAddress)
      )

      connextHelper.getChannelLocalBalance(channelAddress, Currency.WETH).map { response =>
        response mustBe Satoshis.Zero
      }
    }
  }

  private def getConnextHelper(ws: WSClient): ConnextHelper = {
    val config = Configuration(
      "connext.USDT.host" -> "localhost",
      "connext.USDT.port" -> "8005",
      "connext.USDT.publicIdentifier" -> "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj",
      "connext.USDT.chainId" -> "1337",
      "connext.USDT.assetId" -> "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
      "connext.USDT.signerAddress" -> "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65",
      "connext.USDT.withdrawAddress" -> "0x89205A3A3b2A69De6Dbf7f01ED13B2108B2c43e7",
      "connext.WETH.host" -> "localhost",
      "connext.WETH.port" -> "8005",
      "connext.WETH.publicIdentifier" -> "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj",
      "connext.WETH.chainId" -> "1337",
      "connext.WETH.assetId" -> "0xdac17f958d2ee523a2206206994597c13d831ec7",
      "connext.WETH.signerAddress" -> "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65",
      "connext.WETH.withdrawAddress" -> "0x89205A3A3b2A69De6Dbf7f01ED13B2108B2c43e7"
    )

    val connextConfigBuilder = new ConnextConfigBuilder(config)

    new ConnextHelper(connextConfigBuilder, ws)
  }

  private def mockGetRequest(ws: WSClient, url: String, jsonResponse: JsValue): Unit = {
    val request = mock[WSRequest]
    val response = mock[request.Response]

    when(ws.url(url)).thenReturn(request)
    when(request.get()).thenReturn(Future.successful(response))
    when(response.json).thenReturn(jsonResponse)

    ()
  }

  private def getChannelResponse(channelAddress: ConnextChannelAddress): JsValue = {
    Json.parse(
      s"""{
         |  "assetIds": [
         |    "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"
         |  ],
         |  "balances": [
         |    {
         |      "amount": [
         |        "50000000000",
         |        "99999950000000000"
         |      ],
         |      "to": [
         |        "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65",
         |        "0x65b70DfFdAEc7d8818F16f3caD4E86FaDD03993e"
         |      ]
         |    }
         |  ],
         |  "channelAddress": "$channelAddress",
         |  "merkleRoot": "0x0000000000000000000000000000000000000000000000000000000000000000",
         |  "processedDepositsA": [
         |    "0"
         |  ],
         |  "processedDepositsB": [
         |    "100000000000000000"
         |  ],
         |  "defundNonces": [
         |    "1"
         |  ],
         |  "networkContext": {
         |    "chainId": 1337,
         |    "channelFactoryAddress": "0x345cA3e014Aaf5dcA488057592ee47305D9B3e10",
         |    "transferRegistryAddress": "0x9FBDa871d559710256a2502A2517b794B482Db40",
         |    "providerUrl": "http://evm_1337:8545"
         |  },
         |  "nonce": 12,
         |  "alice": "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65",
         |  "aliceIdentifier": "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj",
         |  "bob": "0x65b70DfFdAEc7d8818F16f3caD4E86FaDD03993e",
         |  "bobIdentifier": "vector8ZaxNSdUM83kLXJSsmj5jrcq17CpZUwBirmboaNPtQMEXjVNrL",
         |  "timeout": "86400",
         |  "latestUpdate": {
         |    "assetId": "0x0000000000000000000000000000000000000000",
         |    "balance": {
         |      "amount": [
         |        "50000000000",
         |        "99999950000000000"
         |      ],
         |      "to": [
         |        "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65",
         |        "0x65b70DfFdAEc7d8818F16f3caD4E86FaDD03993e"
         |      ]
         |    },
         |    "channelAddress": "0xB8b06869A32976641a41E75beBF647a1B5F05C9e",
         |    "details": {
         |      "merkleRoot": "0x0000000000000000000000000000000000000000000000000000000000000000",
         |      "transferDefinition": "0xf25186B5081Ff5cE73482AD761DB0eB0d25abfBF",
         |      "transferId": "0x8c4ade7f7ca35a0f905ef944ea8aa25962802627ccaa717fe284eaf52ffb77bd",
         |      "transferResolver": {
         |        "preImage": "0x138006D71B17C9C0B03749659E6D187738BCF9F1D7149916C72F452226729C77"
         |      },
         |      "meta": {}
         |    },
         |    "fromIdentifier": "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj",
         |    "nonce": 12,
         |    "aliceSignature": "0x23a95f06583cdd4ce91131d540f07568d2bc26e40d7d7050e7ad2da07d6015e53bb8d042d2f606c92ca1e65437e612b210d37e12cb8c584943bbfe3fb4355ec51b",
         |    "bobSignature": "0xfce7d8da590e0e48a369bcaaa76dd58b933eeb0d0ee79912aa5bd75c3f225c1f23f7a24817a71f9d1cad7a3b1b44d1d41556056f1ac8134034e977921fb13d041b",
         |    "toIdentifier": "vector8ZaxNSdUM83kLXJSsmj5jrcq17CpZUwBirmboaNPtQMEXjVNrL",
         |    "type": "resolve"
         |  },
         |  "inDispute": false
         |}""".stripMargin
    )
  }

  private def mockPostRequest(ws: WSClient, url: String, jsonResponse: JsValue, status: Int): Unit = {
    val request = mock[WSRequest]
    val response = mock[request.Response]

    when(ws.url(url)).thenReturn(request)
    when(request.post(any[JsValue])(any[BodyWritable[JsValue]])).thenReturn(Future.successful(response))
    when(response.json).thenReturn(jsonResponse)
    when(response.status).thenReturn(status)

    ()
  }

  private def getActiveTransfersResponse(transfer: (PaymentRHash, Int)*): JsValue = {
    Json.parse(
      transfer
        .map { case (paymentHash, amount) =>
          s"""{
          |  "inDispute": false,
          |  "channelFactoryAddress": "0x345cA3e014Aaf5dcA488057592ee47305D9B3e10",
          |  "assetId": "0x0000000000000000000000000000000000000000",
          |  "chainId": 1337,
          |  "channelAddress": "0xB8b06869A32976641a41E75beBF647a1B5F05C9e",
          |  "balance": {
          |    "amount": [
          |      "$amount",
          |      "0"
          |    ],
          |    "to": [
          |      "0x65b70DfFdAEc7d8818F16f3caD4E86FaDD03993e",
          |      "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65"
          |    ]
          |  },
          |  "initiator": "0x65b70DfFdAEc7d8818F16f3caD4E86FaDD03993e",
          |  "responder": "0x1b1968cAfFB691191CC05164d250a4bEF4aFaA65",
          |  "initialStateHash": "0x66e663b8bbca735fe1025326b6083d82fef78871f2c0c2b6fca3e8c5b5bf4008",
          |  "transferDefinition": "0xf25186B5081Ff5cE73482AD761DB0eB0d25abfBF",
          |  "transferEncodings": [
          |    "tuple(bytes32 lockHash, uint256 expiry)",
          |    "tuple(bytes32 preImage)"
          |  ],
          |  "transferId": "0x8007e9e069ea7f556281816d2a8090d606b2178326e4d924565ffefcc35d60f5",
          |  "transferState": {
          |    "lockHash": "0x${paymentHash.toString}",
          |    "expiry": "0"
          |  },
          |  "transferTimeout": "86000",
          |  "meta": {},
          |  "transferResolver": {
          |    "preImage": "0x2520520A353ED85F531B129B0939E4A8738FA13EC03085F99FB608D50A007BFB"
          |  }
          |}""".stripMargin
        }
        .mkString("[", ",", "]")
    )
  }
}
