package io.stakenet.orderbook.models.explorer

import io.stakenet.orderbook.models.Satoshis
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class ExplorerTransaction(id: String, input: List[TransactionValue], output: List[TransactionValue]) {

  val fee: BigDecimal = {
    val vin = input.map(_.value).sum
    val vout = output.map(_.value).sum
    (vin - vout) max 0
  }

  val satoshisFee: Satoshis = {
    Satoshis.from(fee).getOrElse(Satoshis.Zero)
  }
}

object ExplorerTransaction {
  implicit val explorerTransactionReads: Reads[ExplorerTransaction] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "input").read[List[TransactionValue]] and
      (JsPath \ "output").read[List[TransactionValue]]
  )(ExplorerTransaction.apply _)
}
