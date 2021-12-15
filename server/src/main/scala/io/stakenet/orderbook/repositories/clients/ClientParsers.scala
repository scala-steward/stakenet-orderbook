package io.stakenet.orderbook.repositories.clients

import anorm.{Column, Macro, RowParser, SqlParser}
import io.stakenet.orderbook.models.WalletId
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.repositories.CommonParsers._

private[clients] object ClientParsers {

  implicit val walletIdColumn: Column[WalletId] = Column.columnToString
    .map(WalletId.apply)
    .map(_.getOrElse(throw new RuntimeException("Invalid wallet id retrieved from the database")))

  val clientIdParser: RowParser[ClientId] = SqlParser.scalar[ClientId]

  val walletClientParser: RowParser[WalletClient] = Macro.parser[WalletClient](
    "wallet_id",
    "client_id"
  )

  val botMakerClientParser: RowParser[BotMakerClient] = Macro.parser[BotMakerClient](
    "name",
    "client_id",
    "pays_fees"
  )

  val clientPublicKeyParser: RowParser[ClientLndPublicKey] = Macro.parser[ClientLndPublicKey](
    "client_public_key_id",
    "public_key",
    "currency",
    "client_id"
  )

  val clientPublicIdentifierParser: RowParser[ClientConnextPublicIdentifier] =
    Macro.parser[ClientConnextPublicIdentifier](
      "client_public_identifier_id",
      "public_identifier",
      "currency",
      "client_id"
    )
}
