package io.stakenet.orderbook.services

import io.stakenet.orderbook.config.ExplorerConfig
import io.stakenet.orderbook.models.explorer.{EstimatedFee, ExplorerTransaction}
import io.stakenet.orderbook.models.trading.CurrencyPrices
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.services.ExplorerService.ExplorerErrors
import org.slf4j.LoggerFactory
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsError, JsPath, JsSuccess, Reads}
import play.api.libs.ws.WSClient

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait ExplorerService {
  def getUSDPrice(currency: Currency): Future[Either[ExplorerErrors, BigDecimal]]
  def getPrices(currency: Currency): Future[Either[ExplorerErrors, CurrencyPrices]]
  def getTransactionFee(currency: Currency, transactionHash: String): Future[Either[ExplorerErrors, Satoshis]]
  def getEstimateFee(currency: Currency): Future[Either[ExplorerErrors, EstimatedFee]]
  def getLatestBlockNumber(currency: Currency): Future[Either[ExplorerErrors, BigInt]]

  def getTransaction(
      currency: Currency,
      transactionHash: String
  ): Future[Either[ExplorerErrors, ExplorerService.Transaction]]
}

object ExplorerService {

  case class Transaction(blockNumber: BigInt, to: String, value: Satoshis)

  class WSImpl @Inject() (ws: WSClient, explorerConfig: ExplorerConfig)(implicit
      ec: ExecutionContext
  ) extends ExplorerService {

    private val logger = LoggerFactory.getLogger(this.getClass)

    def getUSDPrice(currency: Currency): Future[Either[ExplorerErrors, BigDecimal]] = {
      getPricesRequest(currency)
        .get()
        .map { response =>
          (response.status, response) match {
            case (200, r) =>
              Try(r.json).toOption
                .map { json =>
                  (json \ "usd")
                    .asOpt[BigDecimal]
                    .map(Right(_))
                    .getOrElse(Left(ExplorerErrors.InvalidJsonData(r.body)))
                }
                .getOrElse(Left(ExplorerErrors.GetPriceError(200)))
            case (n, _) => Left(ExplorerErrors.GetPriceError(n))
          }
        }
    }

    def getPrices(currency: Currency): Future[Either[ExplorerErrors, CurrencyPrices]] = {
      getPricesRequest(currency)
        .get()
        .map { response =>
          (response.status, response) match {
            case (200, r) =>
              val result = for {
                json <- Try(r.json).toOption
                btcPrice <- (json \ "btc").asOpt[BigDecimal]
                usdPrice <- (json \ "usd").asOpt[BigDecimal]
              } yield Right(CurrencyPrices(currency, btcPrice, usdPrice, Instant.now))

              result.getOrElse(Left(ExplorerErrors.InvalidJsonData(r.body)))
            case (n, _) =>
              Left(ExplorerErrors.GetPriceError(n))
          }
        }
    }

    def getTransactionFee(currency: Currency, transactionId: String): Future[Either[ExplorerErrors, Satoshis]] = {
      getTransactionsRequest(currency, transactionId).get().map { response =>
        (response.status, response) match {
          case (200, r) =>
            Try(r.json).toOption
              .map { json =>
                json
                  .asOpt[ExplorerTransaction]
                  .map(y => Right(y.satoshisFee))
                  .getOrElse(Left(ExplorerErrors.InvalidJsonData(r.body)))
              }
              .getOrElse(Left(ExplorerErrors.GetPriceError(200)))
          case (n, _) => Left(ExplorerErrors.GetPriceError(n))
        }
      }
    }

    def getEstimateFee(currency: Currency): Future[Either[ExplorerErrors, EstimatedFee]] = {
      getEstimateFeeRequest(currency = currency, blocks = 1).get().map { response =>
        (response.status, response) match {
          case (200, r) =>
            Try(r.json).toOption
              .map { json =>
                (json \ "feerate")
                  .asOpt[BigDecimal]
                  .flatMap(Satoshis.from)
                  .map(x => Right(EstimatedFee(x)))
                  .getOrElse(Left(ExplorerErrors.InvalidJsonData(r.body)))
              }
              .getOrElse(Left(ExplorerErrors.InvalidJsonData(r.body)))
          case (n, _) => Left(ExplorerErrors.GetEstimateFeeError(n))
        }
      }
    }

    private def getPricesRequest(currency: Currency) = {
      val currencyText = currency match {
        case Currency.WETH => "eth" // that's what the api accepts
        case _ => currency.entryName.toLowerCase
      }
      val url = s"${explorerConfig.urlApi}/$currencyText/prices"

      ws.url(url)
        .withHttpHeaders(
          "Accept" -> "application/json"
        )
    }

    private def getTransactionsRequest(currency: Currency, transactionId: String) = {
      val url = s"${explorerConfig.urlApi}/${currency.entryName.toLowerCase}/transactions/$transactionId"

      ws.url(url)
        .withHttpHeaders(
          "Accept" -> "application/json"
        )
    }

    private def getEstimateFeeRequest(currency: Currency, blocks: Int) = {
      val url = s"${explorerConfig.urlApi}/${currency.entryName.toLowerCase}/blocks/estimate-fee?nBlocks=$blocks"
      ws.url(url)
        .withHttpHeaders(
          "Accept" -> "application/json"
        )
    }

    override def getLatestBlockNumber(currency: Currency): Future[Either[ExplorerErrors, BigInt]] = {
      val url = s"${explorerConfig.urlApi}/${getApiPath(currency)}/blocks/latest"

      ws.url(url).addHttpHeaders("Accept" -> "application/json").get().map { response =>
        (response.status, response) match {
          case (200, response) =>
            Try(response.json)
              .map(json => (json \ "number").as[BigInt])
              .toOption
              .toRight(ExplorerErrors.InvalidJsonData(response.body))

          case (code, response) =>
            logger.error(s"$code -> ${response.body}")

            Left(ExplorerErrors.GetLatestBlockNumberError(code))
        }
      }
    }

    override def getTransaction(
        currency: Currency,
        transactionHash: String
    ): Future[Either[ExplorerErrors, Transaction]] = {
      val url = s"${explorerConfig.urlApi}/${getApiPath(currency)}/transactions/$transactionHash"

      ws.url(url).addHttpHeaders("Accept" -> "application/json").get().map { response =>
        (response.status, response) match {
          case (200, response) =>
            implicit val satoshisReads: Reads[Satoshis] = Reads { json =>
              json.validate[BigInt].flatMap { value =>
                Satoshis
                  .from(value, currency.digits)
                  .map(JsSuccess(_))
                  .getOrElse(JsError("Invalid Satoshis"))
              }
            }

            implicit val transactionReads: Reads[ExplorerService.Transaction] = (
              (JsPath \ "blockNumber").read[BigInt] and
                (JsPath \ "to").read[String] and
                (JsPath \ "value").read[Satoshis]
            )(ExplorerService.Transaction.apply _)

            Try(response.json)
              .map(_.as[ExplorerService.Transaction])
              .toOption
              .toRight(ExplorerErrors.InvalidJsonData(response.body))

          case (code, response) =>
            logger.error(s"$code -> ${response.body}")

            Left(ExplorerErrors.GetTransactionError(currency, transactionHash, code))
        }
      }
    }

    private def getApiPath(currency: Currency): String = {
      currency match {
        case Currency.WETH | Currency.ETH | Currency.USDC | Currency.USDT => "eth"
        case currency => currency.entryName.toLowerCase
      }
    }
  }

  sealed trait ExplorerErrors { def getMessage: String }

  object ExplorerErrors {

    case class GetPriceError(code: Int) extends ExplorerErrors {

      override def getMessage: String =
        s"An error occurred while retrieving the USD price from block explorer. code: $code"
    }

    case class InvalidJsonData(json: String) extends ExplorerErrors {
      override def getMessage: String = s"The data received from the block explorer is invalid: $json"
    }

    case class GetTransactionError(currency: Currency, transaction: String, code: Int) extends ExplorerErrors {

      override def getMessage: String =
        s"An error occurred while retrieving the ${currency.entryName} transaction = $transaction from block explorer. code: $code"
    }

    case class GetEstimateFeeError(code: Int) extends ExplorerErrors {

      override def getMessage: String =
        s"An error occurred while retrieving the estimated fee from block explorer. code: $code"
    }

    case class GetLatestBlockNumberError(code: Int) extends ExplorerErrors {
      override def getMessage: String = s"An error occurred while retrieving the latest block number. code: $code"
    }
  }
}
