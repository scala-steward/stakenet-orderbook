package controllers

import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.services.{ChannelService, TradingPairService}
import javax.inject.Inject
import play.api.libs.json.{JsString, Json, Writes}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class TradesController @Inject()(
    cc: ControllerComponents,
    tradingPairsConfig: TradingPairsConfig,
    tradingPairService: TradingPairService,
    channelsService: ChannelService
)(
    implicit ec: ExecutionContext
) extends AbstractController(cc) {
  import TradesController._

  def getVolume(tradingPairStr: String, lastDays: Int): Action[AnyContent] = Action.async { _ =>
    val tradingPairMaybe = TradingPair
      .withNameInsensitiveOption(tradingPairStr)
      .filter(tradingPairsConfig.enabled.contains)

    val result = for {
      _ <- validateLastDays(lastDays).left.map { error =>
        BadRequest(Json.obj("errorMessage" -> error))
      }

      tradingPair <- tradingPairMaybe.toRight(
        NotFound(Json.obj("errorMessage" -> s"Trading pair not found: $tradingPairStr"))
      )
    } yield {
      tradingPairService.getVolume(tradingPair, lastDays).map { tradingPairVolume =>
        val response = Json.obj(
          "pair" -> tradingPairVolume.tradingPair,
          "volumeBTC" -> tradingPairVolume.btcVolume.toBigDecimal,
          "volumeUSD" -> tradingPairVolume.usdVolume.toBigDecimal
        )

        Ok(response)
      }
    }

    result match {
      case Right(response) => response
      case Left(error) => Future.successful(error)
    }
  }

  def getNumberOfTrades(tradingPairStr: String, lastDays: Int): Action[AnyContent] = Action.async { _ =>
    val tradingPairMaybe = TradingPair
      .withNameInsensitiveOption(tradingPairStr)
      .filter(tradingPairsConfig.enabled.contains)

    val result = for {
      _ <- validateLastDays(lastDays).left.map { error =>
        BadRequest(Json.obj("errorMessage" -> error))
      }

      tradingPair <- tradingPairMaybe.toRight(
        NotFound(Json.obj("errorMessage" -> s"Trading pair not found: $tradingPairStr"))
      )
    } yield {
      tradingPairService.getNumberOfTrades(tradingPair, lastDays).map { numberOfTrades =>
        val result = Json.obj(
          "pair" -> tradingPair,
          "trades" -> numberOfTrades
        )
        Ok(result)
      }
    }

    result match {
      case Right(response) => response
      case Left(error) => Future.successful(error)
    }
  }

  def getNodesInfo(tradingPairStr: String): Action[AnyContent] = Action.async { _ =>
    val tradingPairMaybe = TradingPair
      .withNameInsensitiveOption(tradingPairStr)
      .filter(tradingPairsConfig.enabled.contains)

    tradingPairMaybe match {
      case Some(tradingPair) => {
        channelsService.getNodesInfo(tradingPair).map { nodesInfo =>
          val result = Json.obj(
            "pair" -> tradingPair,
            "channels" -> nodesInfo.channels,
            "nodes" -> nodesInfo.nodes
          )
          Ok(result)
        }
      }

      case None =>
        Future.successful(NotFound(Json.obj("errorMessage" -> s"Trading pair not found: ${tradingPairStr}")))
    }
  }

  private def validateLastDays(lastDays: Int): Either[String, Unit] = {
    val maxDays = 50 * 365

    Either.cond(lastDays <= maxDays, (), s"max value for lastDays is $maxDays")
  }
}

object TradesController {
  implicit val currencyWrites: Writes[Currency] = obj => JsString(obj.entryName)
  implicit val tradingPairsWrites: Writes[TradingPair] = obj => JsString(obj.entryName)

}
