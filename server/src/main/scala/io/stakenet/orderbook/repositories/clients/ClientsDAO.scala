package io.stakenet.orderbook.repositories.clients

import java.sql.Connection
import java.time.Instant
import java.util.UUID

import anorm._
import io.stakenet.orderbook.models.clients.Client.{BotMakerClient, WalletClient}
import io.stakenet.orderbook.models.clients.ClientIdentifier.{ClientConnextPublicIdentifier, ClientLndPublicKey}
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients._
import io.stakenet.orderbook.models.{Currency, Satoshis, WalletId}
import io.stakenet.orderbook.repositories.CommonParsers
import org.postgresql.util.{PSQLException, PSQLState}

private[clients] object ClientsDAO {

  private object Constraints {
    val clientsPK = "clients_pk"
    val walletClientsPK = "wallet_clients_pk"
    val walletClientsWalletIdUnique = "wallet_clients_wallet_id_unique"
    val walletClientClientFK = "wallet_client_client_fk"
    val botMakerClientsPK = "bot_maker_clients_pk"
    val botMakerClientsNameUnique = "bot_maker_clients_name_unique"
    val botMakerClientClientFK = "bot_maker_client_client_fk"
    val clientPublicKeysPK = "client_public_keys_pk"
    val clientPublicKeysPublicKeyCurrencyUnique = "client_public_keys_public_key_currency_unique"
    val clientPublicKeysClientFK = "client_public_keys_client_fk"
    val clientsPublicKeysClientIdCurrencyUnique = "clients_public_keys_client_id_currency_unique"
    val clientInfoLogsPK = "client_info_logs_pk"
    val clientInfoLogsClientFK = "client_info_logs_client_fk"
    val clientPublicIdentifiersPK = "client_public_identifiers_pk"

    val clientPublicIdentifiersPublicIdentifierCurrencyUnique =
      "client_public_identifiers_public_identifier_currency_unique"
    val clientPublicIdentifiersClientFK = "client_public_identifiers_client_fk"
    val clientPublicIdentifiersClientIdCurrencyUnique = "client_public_identifiers_client_id_currency_unique"
  }

  def createWalletClient(clientId: ClientId, walletId: WalletId)(implicit conn: Connection): ClientId = {
    try {
      SQL"""
         INSERT INTO wallet_clients(
           wallet_id,
           client_id
         ) VALUES (
           ${walletId.toString},
           ${clientId.toString}::UUID
         )
       """.execute()

      clientId
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.walletClientsPK) =>
        throw new PSQLException(s"wallet client for client $clientId already exist", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.walletClientsWalletIdUnique) =>
        throw new PSQLException(s"wallet client $walletId already exist", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.walletClientClientFK) =>
        throw new PSQLException(s"client $clientId not found", PSQLState.DATA_ERROR)
    }
  }

  def createClient()(implicit conn: Connection): ClientId = {
    val id = ClientId.random()

    try {
      SQL"""
         INSERT INTO clients(client_id) VALUES (${id.toString}::UUID)
       """.execute()

      id
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.clientsPK) =>
        throw new PSQLException(s"client $id already exist", PSQLState.DATA_ERROR)
    }
  }

  def createClientPublicKey(clientId: ClientId, publicKey: Identifier.LndPublicKey, currency: Currency)(implicit
      conn: Connection
  ): Unit = {
    val id = ClientPublicKeyId.random()
    val key = publicKey.value.toArray

    try {
      SQL"""
         INSERT INTO client_public_keys(
           client_public_key_id,
           public_key,
           currency,
           client_id
         ) VALUES (
           ${id.toString}::UUID,
           $key,
           ${currency.entryName}::CURRENCY_TYPE,
           ${clientId.toString}::UUID
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.clientPublicKeysPK) =>
        throw new PSQLException(s"$id already exist", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.clientPublicKeysPublicKeyCurrencyUnique) =>
        throw new PSQLException(s"$publicKey for $currency already registered", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.clientPublicKeysClientFK) =>
        throw new PSQLException(s"client $clientId does not exist", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.clientsPublicKeysClientIdCurrencyUnique) =>
        throw new PSQLException(s"$clientId already has a key for $currency registered", PSQLState.DATA_ERROR)
    }
  }

  def createClientPublicIdentifier(clientId: ClientId, publicIdentifier: ConnextPublicIdentifier, currency: Currency)(
      implicit conn: Connection
  ): Unit = {
    val id = ClientPublicIdentifierId.random()

    try {
      SQL"""
         INSERT INTO client_public_identifiers(
           client_public_identifier_id,
           public_identifier,
           currency,
           client_id
         ) VALUES (
           ${id.toString}::UUID,
           ${publicIdentifier.value},
           ${currency.entryName}::CURRENCY_TYPE,
           ${clientId.toString}::UUID
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.clientPublicIdentifiersPK) =>
        throw new PSQLException(s"$id already exist", PSQLState.DATA_ERROR)
      case error: PSQLException
          if isConstraintError(error, Constraints.clientPublicIdentifiersPublicIdentifierCurrencyUnique) =>
        throw new PSQLException(s"$publicIdentifier for $currency already registered", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.clientPublicIdentifiersClientFK) =>
        throw new PSQLException(s"client $clientId does not exist", PSQLState.DATA_ERROR)
      case error: PSQLException
          if isConstraintError(error, Constraints.clientPublicIdentifiersClientIdCurrencyUnique) =>
        throw new PSQLException(
          s"$clientId already has a public identifier for $currency registered",
          PSQLState.DATA_ERROR
        )
    }
  }

  def findWalletClient(walletId: WalletId)(implicit conn: Connection): Option[WalletClient] = {
    SQL"""
         SELECT wallet_id, client_id FROM wallet_clients WHERE wallet_id = ${walletId.toString}
       """.as(ClientParsers.walletClientParser.singleOpt)
  }

  def findBotMakerClient(secret: String)(implicit conn: Connection): Option[BotMakerClient] = {
    SQL"""
         SELECT name, client_id, pays_fees FROM bot_maker_Clients WHERE secret = $secret
       """.as(ClientParsers.botMakerClientParser.singleOpt)
  }

  def findPublicKey(clientId: ClientId, currency: Currency)(implicit conn: Connection): Option[ClientLndPublicKey] = {
    SQL"""
         SELECT client_public_key_id, public_key, currency, client_id
         FROM client_public_keys
         WHERE client_id = ${clientId.toString}::UUID
           AND currency = ${currency.entryName}::CURRENCY_TYPE
       """.as(ClientParsers.clientPublicKeyParser.singleOpt)
  }

  def findPublicIdentifier(clientId: ClientId, currency: Currency)(implicit
      conn: Connection
  ): Option[ClientConnextPublicIdentifier] = {
    SQL"""
         SELECT client_public_identifier_id, public_identifier, currency, client_id
         FROM client_public_identifiers
         WHERE client_id = ${clientId.toString}::UUID
           AND currency = ${currency.entryName}::CURRENCY_TYPE
       """.as(ClientParsers.clientPublicIdentifierParser.singleOpt)
  }

  def findAllClientIds()(implicit conn: Connection): List[ClientId] = {
    SQL"""
         SELECT client_id
         FROM clients
       """.as(ClientParsers.clientIdParser.*)
  }

  def findAllClientPublicKeys(clientId: ClientId)(implicit conn: Connection): List[ClientLndPublicKey] = {
    SQL"""
         SELECT client_public_key_id, public_key, currency, client_id
         FROM client_public_keys
         WHERE client_id = ${clientId.toString}::UUID
       """.as(ClientParsers.clientPublicKeyParser.*)
  }

  def getPublicKeyRentedCapacity(clientPublicKey: ClientLndPublicKey)(implicit conn: Connection): Satoshis = {
    SQL"""
         SELECT SUM(channel_fee_payments.capacity)
         FROM client_public_keys
         INNER JOIN channels USING(client_public_key_id)
         INNER JOIN channel_fee_payments USING(payment_hash, paying_currency)
         WHERE channels.client_public_key_id = ${clientPublicKey.clientPublicKeyId.toString}::UUID
           AND channels.expires_at >= CURRENT_TIMESTAMP
       """.as(CommonParsers.satoshisParser.singleOpt).getOrElse(Satoshis.Zero)
  }

  def logClientInfo(clientId: ClientId, rentedCapacityUSD: BigDecimal, hubLocalBalanceUSD: BigDecimal, date: Instant)(
      implicit conn: Connection
  ): Unit = {
    val id = UUID.randomUUID()

    try {
      SQL"""
         INSERT INTO client_info_logs(
           client_info_log_id,
           client_id,
           rented_capacity_usd,
           hub_local_balance_usd,
           created_at
         ) VALUES (
           ${id.toString}::UUID,
           ${clientId.toString}::UUID,
           $rentedCapacityUSD,
           $hubLocalBalanceUSD,
           $date
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.clientInfoLogsPK) =>
        throw new PSQLException(s"client info log $id already exists", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.clientInfoLogsClientFK) =>
        throw new PSQLException(s"client $clientId not found", PSQLState.DATA_ERROR)
    }
  }

  def findAllLndClientPublicKeys(currency: Currency)(implicit conn: Connection): List[ClientLndPublicKey] = {
    SQL"""
         SELECT client_public_key_id, public_key, currency, client_id
         FROM client_public_keys
         WHERE currency = ${currency.toString}::CURRENCY_TYPE
       """.as(ClientParsers.clientPublicKeyParser.*)
  }

  def findAllConnextClientPublicidentifiers(
      currency: Currency
  )(implicit conn: Connection): List[ClientConnextPublicIdentifier] = {
    SQL"""
         SELECT client_public_identifier_id, public_identifier, currency, client_id
         FROM client_public_identifiers
         WHERE currency = ${currency.entryName}::CURRENCY_TYPE
       """.as(ClientParsers.clientPublicIdentifierParser.*)
  }

  private def isConstraintError(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
