package io.stakenet.orderbook.repositories

import anorm.{Column, RowParser, SqlParser, SqlRequestError}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, ClientPublicIdentifierId, ClientPublicKeyId, Identifier}
import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.trading.Trade
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}

private[repositories] object CommonParsers {

  def enumColumn[A](f: String => Option[A]): Column[A] = Column.columnToString.mapResult { string =>
    f(string)
      .toRight(SqlRequestError(new RuntimeException(s"The value $string doesn't exists")))
  }

  implicit val currencyColumn: Column[Currency] = enumColumn(Currency.withNameInsensitiveOption)
  implicit val satoshisColumn: Column[Satoshis] =
    Column.columnToBigInt
      .map(Satoshis.from(_, Satoshis.Digits))
      .map(_.getOrElse(throw new RuntimeException("Corrupted satoshis")))
  implicit val orderIdColumn: Column[OrderId] = Column.columnToUUID.map(OrderId.apply)
  implicit val PaymentHashColumn: Column[PaymentRHash] =
    Column.columnToByteArray.map(_.toVector).map(PaymentRHash.apply)
  implicit val publicKeyColumn: Column[Identifier.LndPublicKey] =
    Column.columnToByteArray.map(x => Identifier.LndPublicKey(x.toVector))
  implicit val connextPublicIdentifierColumn: Column[ConnextPublicIdentifier] = Column.columnToString
    .map(ConnextPublicIdentifier.untrusted)
    .map(_.getOrElse(throw new RuntimeException("Invalid connext public identifier retrieved from the database")))
  implicit val clientPublicKeyIdColumn: Column[ClientPublicKeyId] = Column.columnToUUID.map(ClientPublicKeyId.apply)
  implicit val clientPublicIdentifierIdColumn: Column[ClientPublicIdentifierId] =
    Column.columnToUUID.map(ClientPublicIdentifierId.apply)
  implicit val clientIdColumn: Column[ClientId] = Column.columnToUUID.map(ClientId.apply)
  implicit val tradeIdColumn: Column[Trade.Id] = Column.columnToUUID.map(Trade.Id.apply)

  val satoshisParser: RowParser[Satoshis] = SqlParser.scalar[Satoshis]
  val bigIntParser: RowParser[BigInt] = SqlParser.scalar[BigInt]
}
