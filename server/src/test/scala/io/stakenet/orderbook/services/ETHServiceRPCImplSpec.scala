package io.stakenet.orderbook.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.stakenet.orderbook.helpers.Executors
import io.stakenet.orderbook.models.Satoshis
import org.mockito.MockitoSugar.{doReturn, mock}
import org.scalactic.Equality
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.{EthBlock, EthBlockNumber, EthTransaction}
import org.web3j.protocol.core.{DefaultBlockParameter, JsonRpc2_0Web3j, Request}
import org.scalatest.OptionValues._

import scala.compat.java8.FutureConverters
import scala.concurrent.Future

class ETHServiceRPCImplSpec extends AsyncWordSpec with Matchers {

  implicit val blockParameterEquality: Equality[DefaultBlockParameter] = (a: DefaultBlockParameter, b: Any) => {
    b match {
      case c: DefaultBlockParameter => a.getValue == c.getValue
      case _ => false
    }
  }

  "getLatestBlockNumber" should {
    "get latest block number" in {
      val ethClient = mock[JsonRpc2_0Web3j]
      val service = getService(ethClient)

      val request = mock[Request[_, EthBlockNumber]]
      val response =
        """{
          |  "jsonrpc": "2.0",
          |  "id": 1,
          |  "result": "0xa9e699"
          |}""".stripMargin
      val blockNumber = new ObjectMapper().readValue(response, classOf[EthBlockNumber])
      doReturn(request).when(ethClient).ethBlockNumber()
      doReturn(FutureConverters.toJava(Future.successful(blockNumber)).toCompletableFuture).when(request).sendAsync()

      service.getLatestBlockNumber().map { result =>
        result mustBe BigInt("11134617")
      }
    }

    "fail when there is not result" in {
      val ethClient = mock[JsonRpc2_0Web3j]
      val service = getService(ethClient)

      val request = mock[Request[_, EthBlock]]
      val response =
        """{
          |  "jsonrpc": "2.0",
          |  "id": 1,
          |  "result": null
          |}""".stripMargin
      val blockNumber = new ObjectMapper().readValue(response, classOf[EthBlockNumber])
      doReturn(request).when(ethClient).ethBlockNumber()
      doReturn(FutureConverters.toJava(Future.successful(blockNumber)).toCompletableFuture).when(request).sendAsync()

      recoverToSucceededIf[ETHService.Error.UnexpectedResponse](service.getLatestBlockNumber())
    }

    "fail when eth node returns an error" in {
      val ethClient = mock[JsonRpc2_0Web3j]
      val service = getService(ethClient)

      val request = mock[Request[_, EthBlock]]
      val response =
        """{
          |  "jsonrpc":"2.0",
          |  "error":{
          |     "code":-32000,
          |     "message":"Requested block number is in a range that is not available yet, because the ancient block sync is still in progress."
          |  },
          |  "id":1
          |}""".stripMargin
      val blockNumber = new ObjectMapper().readValue(response, classOf[EthBlockNumber])
      doReturn(request).when(ethClient).ethBlockNumber()
      doReturn(FutureConverters.toJava(Future.successful(blockNumber)).toCompletableFuture).when(request).sendAsync()

      recoverToSucceededIf[ETHService.Error.CouldNotGetLatestBlockNumber](service.getLatestBlockNumber())
    }
  }

  "getTransaction" should {
    "get a transaction" in {
      val ethClient = mock[JsonRpc2_0Web3j]
      val service = getService(ethClient)

      val hash = "0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"
      val request = mock[Request[_, EthTransaction]]
      val response =
        s"""{
          |  "jsonrpc": "2.0",
          |  "id": 1,
          |  "result": {
          |     "blockHash": "0xbe9fed17ee6c5009bc4cea8f3f0bc16e88c7d8c8cb86c0c1a123194448ef4052",
          |     "blockNumber": "0xabcde",
          |     "from": "0xd1e56c2e765180aa0371928fd4d1e41fbcda34d4",
          |     "gas": "0x15f90",
          |     "gasPrice": "0xba43b7400",
          |     "hash": "0xe352a785343d4fcbd5e41a7c9152bae1230365cfe4a7a149310546b60192861f",
          |     "input": "0x",
          |     "nonce": "0x59b",
          |     "r": "0x132c11aba6c2e71cc513185717de62aa8e2fd00f768f8aaa58803e9a3000ffce",
          |     "s": "0x56424e6f0d53917c8cdbcd007d1ed11f2e69c2c7e49bd40f80d61efac24d33fb",
          |     "to": "0xfabfcdb9ad54126a8829d5ab6357f608c6b005d0",
          |     "transactionIndex": "0x0",
          |     "v": "0x1b",
          |     "value": "0x280b2985d2811a00"
          |  }
          |}""".stripMargin
      val transaction = new ObjectMapper().readValue(response, classOf[EthTransaction])
      doReturn(request).when(ethClient).ethGetTransactionByHash(hash)
      doReturn(FutureConverters.toJava(Future.successful(transaction)).toCompletableFuture).when(request).sendAsync()

      service.getTransaction(hash).map { result =>
        result mustBe ETHService.Transaction(
          BigInt(703710),
          "0xfabfcdb9ad54126a8829d5ab6357f608c6b005d0",
          Satoshis.from(BigDecimal("2.885445641")).value
        )
      }
    }

    "fail when there is not result" in {
      val ethClient = mock[JsonRpc2_0Web3j]
      val service = getService(ethClient)

      val hash = "0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"
      val request = mock[Request[_, EthTransaction]]
      val response =
        """{
          |  "jsonrpc": "2.0",
          |  "id": 1,
          |  "result": null
          |}""".stripMargin
      val transaction = new ObjectMapper().readValue(response, classOf[EthTransaction])
      doReturn(request).when(ethClient).ethGetTransactionByHash(hash)
      doReturn(FutureConverters.toJava(Future.successful(transaction)).toCompletableFuture).when(request).sendAsync()

      recoverToSucceededIf[ETHService.Error.UnexpectedResponse](service.getTransaction(hash))
    }

    "fail when eth node returns an error" in {
      val ethClient = mock[JsonRpc2_0Web3j]
      val service = getService(ethClient)

      val hash = "0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"
      val request = mock[Request[_, EthTransaction]]
      val response =
        """{
          |  "jsonrpc":"2.0",
          |  "error":{
          |     "code":-32000,
          |     "message":"Requested block number is in a range that is not available yet, because the ancient block sync is still in progress."
          |  },
          |  "id":1
          |}""".stripMargin
      val transaction = new ObjectMapper().readValue(response, classOf[EthTransaction])
      doReturn(request).when(ethClient).ethGetTransactionByHash(hash)
      doReturn(FutureConverters.toJava(Future.successful(transaction)).toCompletableFuture).when(request).sendAsync()

      recoverToSucceededIf[ETHService.Error.CouldNotGetTransaction](service.getTransaction(hash))
    }
  }

  private def getService(ethClient: Web3j): ETHService = {
    new ETHServiceRPCImpl(ethClient)(Executors.globalEC)
  }
}
