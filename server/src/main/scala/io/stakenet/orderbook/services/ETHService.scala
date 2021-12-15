package io.stakenet.orderbook.services

import io.stakenet.orderbook.models.{Currency, Satoshis}
import javax.inject.Inject
import org.web3j.protocol.Web3j

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.jdk.OptionConverters._

trait ETHService {

  def getLatestBlockNumber(): Future[BigInt]
  def getTransaction(transactionHash: String): Future[ETHService.Transaction]

}

class ETHServiceRPCImpl @Inject()(ethClient: Web3j)(implicit ec: ExecutionContext) extends ETHService {
  override def getLatestBlockNumber(): Future[BigInt] = {
    FutureConverters
      .toScala(ethClient.ethBlockNumber().sendAsync())
      .map { result =>
        (Try(Option(result.getBlockNumber)).toOption.flatten, Option(result.getError)) match {
          case (Some(number), _) =>
            number

          case (_, Some(error)) =>
            throw new ETHService.Error.CouldNotGetLatestBlockNumber(error.getCode, error.getMessage)

          case _ =>
            throw new ETHService.Error.UnexpectedResponse()
        }
      }
  }

  override def getTransaction(transactionHash: String): Future[ETHService.Transaction] = {
    FutureConverters.toScala(ethClient.ethGetTransactionByHash(transactionHash).sendAsync()).map { result =>
      (Try(result.getTransaction.toScala).toOption.flatten, Option(result.getError)) match {
        case (Some(transaction), _) =>
          val amount = Currency.ETH
            .satoshis(transaction.getValue)
            .getOrElse(throw new ETHService.Error.InvalidSatohis(transaction.getValue))

          ETHService.Transaction(transaction.getBlockNumber, transaction.getTo, amount)

        case (_, Some(error)) =>
          throw new ETHService.Error.CouldNotGetTransaction(transactionHash, error.getCode, error.getMessage)

        case _ =>
          throw new ETHService.Error.UnexpectedResponse()
      }
    }
  }
}

object ETHService {

  case class Transaction(blockNumber: BigInt, to: String, value: Satoshis)

  object Error {

    class CouldNotGetLatestBlockNumber(code: Int, message: String) extends RuntimeException {
      override def getMessage: String = {
        s"An error occurred getting latest block number: $message(error code: $code)"
      }
    }

    class UnexpectedResponse() extends RuntimeException {
      override def getMessage: String = {
        s"Unexpected response"
      }
    }

    class CouldNotGetTransaction(hash: String, code: Int, message: String) extends RuntimeException {
      override def getMessage: String = {
        s"An error occurred getting transaction $hash: $message(error code: $code)"
      }
    }

    class InvalidSatohis(amount: BigInt) extends RuntimeException {
      override def getMessage: String = {
        amount.toString
      }
    }
  }
}
