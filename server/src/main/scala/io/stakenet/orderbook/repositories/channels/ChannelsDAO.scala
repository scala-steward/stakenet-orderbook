package io.stakenet.orderbook.repositories.channels

import java.sql.Connection
import java.time.Instant

import anorm._
import io.stakenet.orderbook.models.ChannelIdentifier.{ConnextChannelAddress, LndOutpoint}
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.connext.{ConnextChannel, ConnextChannelContractDeploymentFee}
import io.stakenet.orderbook.models.lnd._
import org.postgresql.util.{PSQLException, PSQLState}

private[channels] object ChannelsDAO {

  object Constraints {
    val ChannelsPaymentHashPayingCurrencyUnique = "channels_payment_hash_paying_currency_unique"
    val ChannelChannelFeePaymentFK = "channel_channel_fee_payment_fk"
    val ChannelsPK = "channels_pk"

    val ChannelExtensionRequestsPK = "channel_extension_requests_pk"
    val ChannelExtensionRequestsChannelsFk = "channel_extension_requests_channels_fk"
    val ChannelExtensionFeePaymentsPK = "channel_extension_fee_payments_pk"

    val ChannelExtensionFeePaymentsChannelExtensionRequestsFK =
      "channel_extension_fee_payments_channel_extension_requests_fk"

    val CloseExpiredChannelRequestPK = "close_expired_channel_request_pk"
    val CloseExpiredChannelRequestChannelFK = "close_expired_channel_request_channel_fk"
    val ChannelsClientPublicKeysFK = "channels_client_public_keys_fk"

    val connextChannelsPK = "connext_channels_pk"
    val connextChannelsFeePaymentUnique = "connext_channels_fee_payment_unique"
    val connextChannelsFeePaymentFK = "connext_channels_fee_payment_fk"
    val connextChannelsClientPublicIdentifierFK = "connext_channels_client_public_identifier_fk"

    val ConnextChannelExtensionRequestsPK = "connext_channel_extension_requests_pk"
    val ConnextChannelExtensionRequestsConnextChannelsFK = "connext_channel_extension_requests_connext_channels_fk"
    val ConnextChannelExtensionFeePaymentsPK = "connext_channel_extension_fee_payments_pk"
    val connextChannelExtensionFeePaymentsRequestsFK = "connext_channel_extension_fee_payments_requests_fk"

    val connextChannelContractDeploymentFeesPK = "connext_channel_contract_deployment_fees_pk"
    val connextChannelContractDeploymentFeesClientsFK = "connext_channel_contract_deployment_fees_clients_fk"
    val connextChannelContractDeploymentFeesClientIdUnique = "connext_channel_contract_deployment_fees_client_id_unique"
  }

  def createChannelPayment(channelFeePayment: ChannelFeePayment, paymentRHash: PaymentRHash, fee: Satoshis)(implicit
      conn: Connection
  ): Unit = {
    try {
      val paymentHash = paymentRHash.value.toArray
      val _ = SQL"""
        INSERT INTO channel_fee_payments
          (currency, paying_currency, capacity, life_time_seconds, fee, payment_hash)
        VALUES (
          ${channelFeePayment.currency.entryName}::CURRENCY_TYPE,
          ${channelFeePayment.payingCurrency.entryName}::CURRENCY_TYPE,
          ${channelFeePayment.capacity.value(Satoshis.Digits)}::SATOSHIS_TYPE,
          ${channelFeePayment.lifeTimeSeconds},
          ${fee.value(Satoshis.Digits)},
          $paymentHash
          
        )
        """
        .execute()
    } catch {
      case e: PSQLException
          if e.getServerErrorMessage.getMessage == "duplicate key value violates unique constraint \"channel_fee_payments_pk\"" =>
        throw new PSQLException("Payment hash already exists", PSQLState.DATA_ERROR)
    }
  }

  def findChannelFeePayment(paymentRHash: PaymentRHash, currency: Currency)(implicit
      conn: Connection
  ): Option[ChannelFeePayment] = {
    val hashValue = paymentRHash.value.toArray

    SQL"""
        SELECT currency, paying_currency, capacity, life_time_seconds, fee
        FROM channel_fee_payments
        WHERE payment_hash = $hashValue AND paying_currency = ${currency.entryName}::CURRENCY_TYPE
     """.as(ChannelParsers.channelPaymentParser.singleOpt)
  }

  def createChannel(channel: Channel.LndChannel)(implicit conn: Connection): Unit = {
    try {
      val publicKey = channel.publicKey.value.toArray
      val paymentRHash = channel.paymentRHash.value.toArray
      val _ = SQL"""
        INSERT INTO channels
          (channel_id, payment_hash, paying_currency, public_key, client_public_key_id, channel_status)
        VALUES (
          ${channel.channelId.value}::UUID,
          $paymentRHash,
          ${channel.payingCurrency.entryName}::CURRENCY_TYPE,
          $publicKey,
          ${channel.clientPublicKeyId.toString}::UUID,
          ${channel.status.entryName}::CHANNEL_STATUS
        )
        """
        .execute()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.ChannelsPK) =>
        throw new PSQLException("The channel already exists", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.ChannelsPaymentHashPayingCurrencyUnique) =>
        throw new PSQLException("The payment hash already exists", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.ChannelChannelFeePaymentFK) =>
        throw new PSQLException("Fee payment not found", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.ChannelsClientPublicKeysFK) =>
        throw new PSQLException("Client public key not found", PSQLState.DATA_ERROR)
    }
  }

  def createChannel(channel: Channel.ConnextChannel, transactionHash: String)(implicit conn: Connection): Unit = {
    try {
      val paymentRHash = channel.paymentRHash.value.toArray

      val _ = SQL"""
        INSERT INTO connext_channels(
          connext_channel_id,
          client_public_identifier_id,
          payment_hash,
          paying_currency,
          channel_address,
          status,
          transaction_hash,
          created_at,
          expires_at
        ) VALUES (
          ${channel.channelId.value}::UUID,
          ${channel.clientPublicIdentifierId.uuid}::UUID,
          $paymentRHash,
          ${channel.payingCurrency.entryName}::CURRENCY_TYPE,
          ${channel.channelAddress.map(_.toString)},
          ${channel.status.entryName}::CONNEXT_CHANNEL_STATUS,
          $transactionHash,
          ${channel.createdAt},
          ${channel.expiresAt}
        )
        """
        .execute()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelsPK) =>
        throw new PSQLException(s"Channel ${channel.channelId} already exists", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelsFeePaymentUnique) =>
        throw new PSQLException(
          s"Channel for (${channel.paymentRHash} ${channel.payingCurrency}) already exist",
          PSQLState.DATA_ERROR
        )
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelsFeePaymentFK) =>
        throw new PSQLException(
          s"Fee payment (${channel.paymentRHash} ${channel.payingCurrency}) not found",
          PSQLState.DATA_ERROR
        )
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelsClientPublicIdentifierFK) =>
        throw new PSQLException(
          s"public identifier ${channel.clientPublicIdentifierId} not found",
          PSQLState.DATA_ERROR
        )
    }
  }

  def findChannel(channelId: ChannelId.LndChannelId)(implicit
      conn: Connection
  ): Option[Channel.LndChannel] = {

    SQL"""
        SELECT
          channel_id, payment_hash, public_key, funding_transaction, output_index, created_at, expires_at,
          channel_status, closing_type, closed_by, closed_on, paying_currency, client_public_key_id
        FROM channels
        WHERE channel_id = ${channelId.value.toString}::UUID
     """.as(ChannelParsers.channelParser.singleOpt)
  }

  def findChannel(paymentHash: PaymentRHash, currency: Currency)(implicit
      conn: Connection
  ): Option[Channel.LndChannel] = {
    val hash = paymentHash.value.toArray

    SQL"""
        SELECT
          channel_id, payment_hash, public_key, funding_transaction, output_index, created_at, expires_at,
          channel_status, closing_type, closed_by, closed_on, paying_currency, client_public_key_id
        FROM channels
        WHERE payment_hash = $hash AND paying_currency = ${currency.entryName}::CURRENCY_TYPE
     """.as(ChannelParsers.channelParser.singleOpt)
  }

  def findConnextChannel(paymentHash: PaymentRHash, currency: Currency)(implicit
      conn: Connection
  ): Option[Channel.ConnextChannel] = {
    val hash = paymentHash.value.toArray

    SQL"""
        SELECT
          c.connext_channel_id, pi.public_identifier, c.client_public_identifier_id, c.payment_hash, c.paying_currency,
          c.channel_address, c.status, c.created_at, expires_at
        FROM connext_channels c
        INNER JOIN client_public_identifiers pi USING(client_public_identifier_id)
        WHERE payment_hash = $hash AND paying_currency = ${currency.entryName}::CURRENCY_TYPE
     """.as(ChannelParsers.connextChannelParser.singleOpt)
  }

  def findConnextChannel(id: ChannelId.ConnextChannelId)(implicit
      conn: Connection
  ): Option[Channel.ConnextChannel] = {
    SQL"""
        SELECT
          c.connext_channel_id, pi.public_identifier, c.client_public_identifier_id, c.payment_hash, c.paying_currency,
          c.channel_address, c.status, c.created_at, expires_at
        FROM connext_channels c
        INNER JOIN client_public_identifiers pi USING(client_public_identifier_id)
        WHERE connext_channel_id = ${id.value.toString}::UUID
     """.as(ChannelParsers.connextChannelParser.singleOpt)
  }

  def findChannelForUpdate(channelId: ChannelId.LndChannelId)(implicit
      conn: Connection
  ): Option[Channel.LndChannel] = {

    SQL"""
        SELECT
          channel_id, payment_hash, public_key, funding_transaction, output_index, created_at, expires_at,
          channel_status, closing_type, closed_by, closed_on, paying_currency, client_public_key_id
        FROM channels
        WHERE channel_id = ${channelId.value.toString}::UUID
        FOR UPDATE NOWAIT
     """.as(ChannelParsers.channelParser.singleOpt)
  }

  def findChannelForUpdate(channelId: ChannelId.ConnextChannelId)(implicit
      conn: Connection
  ): Option[Channel.ConnextChannel] = {
    SQL"""
        SELECT
          c.connext_channel_id, pi.public_identifier, c.client_public_identifier_id, c.payment_hash, c.paying_currency,
          c.channel_address, c.status, c.created_at, expires_at
        FROM connext_channels c
        INNER JOIN client_public_identifiers pi USING(client_public_identifier_id)
        WHERE connext_channel_id = ${channelId.value.toString}::UUID
        FOR UPDATE NOWAIT
     """.as(ChannelParsers.connextChannelParser.singleOpt)
  }

  def updateChannelStatus(channelId: ChannelId.LndChannelId, channelStatus: ChannelStatus)(implicit
      conn: Connection
  ): Unit = {

    val updatedRows = SQL"""
        UPDATE channels
        SET channel_status = ${channelStatus.entryName}::CHANNEL_STATUS
        WHERE channel_id = ${channelId.value}::UUID
        """
      .executeUpdate()
    assert(
      updatedRows == 1,
      s"The channel status wasn't updated, likely due to a race condition, channelId = $channelId, channelStatus = $channelStatus"
    )
  }

  def updateChannelStatus(channelAddress: ConnextChannelAddress, channelStatus: ConnextChannelStatus)(implicit
      conn: Connection
  ): Unit = {

    val updatedRows = SQL"""
        UPDATE connext_channels
        SET status = ${channelStatus.entryName}::CONNEXT_CHANNEL_STATUS
        WHERE channel_address = ${channelAddress.toString}
        """
      .executeUpdate()

    assert(
      updatedRows == 1,
      s"The channel status wasn't updated, likely due to a race condition, channelAddress = $channelAddress, channelStatus = $channelStatus"
    )
  }

  def updateChannelPoint(channelId: ChannelId.LndChannelId, outPoint: LndOutpoint)(implicit
      conn: Connection
  ): Unit = {
    val fundingTransaction = outPoint.txid.bigEndianBytes.toArray
    val updatedRows = SQL"""
        UPDATE channels
        SET funding_transaction = $fundingTransaction,
          output_index = ${outPoint.index},
          channel_status = ${ChannelStatus.Opening.entryName}::CHANNEL_STATUS
        WHERE channel_id = ${channelId.value}::UUID
        """
      .executeUpdate()
    assert(
      updatedRows == 1,
      s"The channel point wasn't updated, likely due to a race condition, channelId = $channelId"
    )
  }

  def updateActiveChannel(channelId: ChannelId.LndChannelId, createdAt: Instant, expiresAt: Instant)(implicit
      conn: Connection
  ): Unit = {
    val updatedRows = SQL"""
        UPDATE channels
        SET created_at = $createdAt,
          expires_at = $expiresAt,
          channel_status = ${ChannelStatus.Active.entryName}::CHANNEL_STATUS
        WHERE channel_id = ${channelId.value}::UUID
        """
      .executeUpdate()
    assert(
      updatedRows == 1,
      s"The active channel wasn't updated, likely due to a race condition, channelId = $channelId"
    )
  }

  def updateActiveChannel(outpoint: LndOutpoint, createdAt: Instant, expiresAt: Instant)(implicit
      conn: Connection
  ): Unit = {
    val fundingTransaction = outpoint.txid.bigEndianBytes.toArray
    val outputIndex = outpoint.index

    val updatedRows = SQL"""
        UPDATE channels
        SET created_at = $createdAt,
          expires_at = $expiresAt,
          channel_status = ${ChannelStatus.Active.entryName}::CHANNEL_STATUS
        WHERE funding_transaction = $fundingTransaction
          AND output_index = $outputIndex
        """
      .executeUpdate()
    assert(
      updatedRows == 1,
      s"The active channel wasn't updated, likely due to a race condition, outpoint = $outpoint"
    )
  }

  def updateClosedChannel(outPoint: LndOutpoint, closingType: String, closedBy: String, closedOn: Instant)(implicit
      conn: Connection
  ): Unit = {
    val fundingTransaction = outPoint.txid.bigEndianBytes.toArray
    val outputIndex = outPoint.index

    val updatedRows = SQL"""
        UPDATE channels
        SET closing_type = $closingType,
          closed_by = $closedBy,
          closed_on = $closedOn,
          channel_status = ${ChannelStatus.Closed.entryName}::CHANNEL_STATUS
        WHERE funding_transaction = $fundingTransaction
          AND output_index = $outputIndex
        """
      .executeUpdate()

    assert(
      updatedRows == 1,
      s"The closed channel wasn't updated, likely due to a race condition, outpoint = $outPoint"
    )
  }

  def updateChannelExpirationDate(channelId: ChannelId.LndChannelId, expiresAt: Instant)(implicit
      conn: Connection
  ): Unit = {
    val updatedRows = SQL"""
        UPDATE channels
        SET expires_at = $expiresAt
        WHERE channel_id = ${channelId.value}::UUID
        """
      .executeUpdate()

    assert(updatedRows == 1, s"The Channel expiration date wasn't updated, channelId = $channelId")
  }

  def updateChannelExpirationDate(channelId: ChannelId.ConnextChannelId, expiresAt: Instant)(implicit
      conn: Connection
  ): Unit = {
    val updatedRows = SQL"""
        UPDATE connext_channels
        SET expires_at = $expiresAt
        WHERE connext_channel_id = ${channelId.value}::UUID
        """
      .executeUpdate()

    assert(updatedRows == 1, s"The Channel expiration date wasn't updated, channelId = $channelId")
  }

  def getExpiredChannels(currency: Currency)(implicit
      conn: Connection
  ): List[LndChannel] = {
    SQL"""
        SELECT c.channel_id, fp.currency, c.funding_transaction, c.output_index, fp.life_time_seconds, c.channel_status
        FROM channels AS c
        JOIN channel_fee_payments AS fp
          USING (payment_hash)
        WHERE c.channel_status = 'ACTIVE'::CHANNEL_STATUS
        	AND c.expires_at <= CURRENT_TIMESTAMP
          AND fp.currency = ${currency.entryName}::CURRENCY_TYPE
          AND c.funding_transaction NOTNULL
        """
      .as(ChannelParsers.lndChannelParser.*)
  }

  def getConnextExpiredChannels()(implicit conn: Connection): List[Channel.ConnextChannel] = {
    SQL"""
        SELECT
          c.connext_channel_id, pi.public_identifier, c.client_public_identifier_id, c.payment_hash, c.paying_currency,
          c.channel_address, c.status, c.created_at, expires_at
        FROM connext_channels c
        INNER JOIN client_public_identifiers pi USING(client_public_identifier_id)
        INNER JOIN channel_fee_payments AS fp USING (payment_hash)
        WHERE c.status = 'ACTIVE'::CONNEXT_CHANNEL_STATUS
        	AND c.expires_at <= CURRENT_TIMESTAMP
        """
      .as(ChannelParsers.connextChannelParser.*)
  }

  def getProcessingChannels(currency: Currency)(implicit
      conn: Connection
  ): List[LndChannel] = {
    SQL"""
        SELECT c.channel_id, fp.currency, c.funding_transaction, c.output_index, fp.life_time_seconds, c.channel_status
        FROM channels AS c
        JOIN channel_fee_payments AS fp
          USING (payment_hash)
        WHERE c.channel_status IN('OPENING'::CHANNEL_STATUS, 'CLOSING'::CHANNEL_STATUS) 
          AND fp.currency = ${currency.entryName}::CURRENCY_TYPE
          AND funding_transaction NOTNULL
        """
      .as(ChannelParsers.lndChannelParser.*)
  }

  def findChannelFeePayment(channelId: ChannelId.LndChannelId)(implicit conn: Connection): Option[ChannelFeePayment] = {
    SQL"""
        SELECT
          fp.currency, fp.paying_currency, fp.capacity, fp.life_time_seconds, fp.fee
        FROM channels AS c
        JOIN channel_fee_payments AS fp USING (payment_hash, paying_currency)
        WHERE c.channel_id = ${channelId.value}::UUID
       """
      .as(ChannelParsers.channelPaymentParser.singleOpt)
  }

  def findChannelFeePayment(
      channelId: ChannelId.ConnextChannelId
  )(implicit conn: Connection): Option[ChannelFeePayment] = {
    SQL"""
        SELECT
          fp.currency, fp.paying_currency, fp.capacity, fp.life_time_seconds, fp.fee
        FROM connext_channels AS c
        JOIN channel_fee_payments AS fp USING (payment_hash, paying_currency)
        WHERE c.connext_channel_id = ${channelId.value}::UUID
       """
      .as(ChannelParsers.channelPaymentParser.singleOpt)
  }

  def createRentedChannelExtensionRequest(
      channelExtension: ChannelExtension[ChannelId.LndChannelId]
  )(implicit conn: Connection): Unit = {
    val paymentHash = channelExtension.paymentHash.value.toArray

    try {
      val _ =
        SQL"""
          INSERT INTO channel_extension_requests
            (payment_hash, paying_currency, channel_id, fee, seconds, requested_at)
          VALUES (
            $paymentHash,
            ${channelExtension.payingCurrency.entryName}::CURRENCY_TYPE,
            ${channelExtension.channelId.toString}::UUID,
            ${channelExtension.fee.value(Satoshis.Digits)}::SATOSHIS_TYPE,
            ${channelExtension.seconds},
            ${channelExtension.requestedAt}
          )
          """
          .execute()
    } catch {
      case error: PSQLException if violatesConstraint(error, Constraints.ChannelExtensionRequestsPK) =>
        throw new PSQLException(
          s"Extension request for ${channelExtension.paymentHash} in ${channelExtension.payingCurrency} already exist",
          PSQLState.DATA_ERROR
        )
      case error: PSQLException if violatesConstraint(error, Constraints.ChannelExtensionRequestsChannelsFk) =>
        throw new PSQLException(
          s"Channel ${channelExtension.channelId} was not found",
          PSQLState.DATA_ERROR
        )
    }
  }

  def createConnextChannelExtensionRequest(
      channelExtension: ChannelExtension[ChannelId.ConnextChannelId]
  )(implicit conn: Connection): Unit = {
    val paymentHash = channelExtension.paymentHash.value.toArray

    try {
      val _ =
        SQL"""
          INSERT INTO connext_channel_extension_requests(
            payment_hash,
            paying_currency,
            connext_channel_id,
            fee,
            seconds,
            requested_at
          ) VALUES (
            $paymentHash,
            ${channelExtension.payingCurrency.entryName}::CURRENCY_TYPE,
            ${channelExtension.channelId.toString}::UUID,
            ${channelExtension.fee.value(Satoshis.Digits)}::SATOSHIS_TYPE,
            ${channelExtension.seconds},
            ${channelExtension.requestedAt}
          )
          """
          .execute()
    } catch {
      case error: PSQLException if violatesConstraint(error, Constraints.ConnextChannelExtensionRequestsPK) =>
        throw new PSQLException(
          s"Extension request for ${channelExtension.paymentHash} in ${channelExtension.payingCurrency} already exist",
          PSQLState.DATA_ERROR
        )
      case error: PSQLException
          if violatesConstraint(error, Constraints.ConnextChannelExtensionRequestsConnextChannelsFK) =>
        throw new PSQLException(
          s"Channel ${channelExtension.channelId} was not found",
          PSQLState.DATA_ERROR
        )
    }
  }

  def createRentedChannelExtensionFeePayment(paymentHash: PaymentRHash, payingCurrency: Currency, paidAt: Instant)(
      implicit conn: Connection
  ): Unit = {
    val hash = paymentHash.value.toArray

    try {
      val _ =
        SQL"""
          INSERT INTO channel_extension_fee_payments(payment_hash, paying_currency, paid_at)
          VALUES (
            $hash,
            ${payingCurrency.entryName}::CURRENCY_TYPE,
            $paidAt
          )
          """
          .execute()
    } catch {
      case error: PSQLException if violatesConstraint(error, Constraints.ChannelExtensionFeePaymentsPK) =>
        throw new PSQLException(
          s"Extension fee payment for $paymentHash in $payingCurrency already exist",
          PSQLState.DATA_ERROR
        )
      case error: PSQLException
          if violatesConstraint(error, Constraints.ChannelExtensionFeePaymentsChannelExtensionRequestsFK) =>
        throw new PSQLException(
          s"Extension fee request for $paymentHash in $payingCurrency was not found",
          PSQLState.DATA_ERROR
        )
    }
  }

  def createConnextRentedChannelExtensionFeePayment(
      paymentHash: PaymentRHash,
      payingCurrency: Currency,
      paidAt: Instant
  )(implicit conn: Connection): Unit = {
    val hash = paymentHash.value.toArray

    try {
      val _ =
        SQL"""
            INSERT INTO connext_channel_extension_fee_payments(payment_hash, paying_currency, paid_at)
            VALUES ($hash, ${payingCurrency.entryName}::CURRENCY_TYPE, $paidAt)
           """.execute()
    } catch {
      case error: PSQLException if violatesConstraint(error, Constraints.ConnextChannelExtensionFeePaymentsPK) =>
        throw new PSQLException(
          s"Extension fee payment for $paymentHash in $payingCurrency already exist",
          PSQLState.DATA_ERROR
        )
      case error: PSQLException
          if violatesConstraint(error, Constraints.connextChannelExtensionFeePaymentsRequestsFK) =>
        throw new PSQLException(
          s"Extension fee request for $paymentHash in $payingCurrency was not found",
          PSQLState.DATA_ERROR
        )
    }
  }

  def findChannelExtension(paymentHash: PaymentRHash, payingCurrency: Currency)(implicit
      conn: Connection
  ): Option[ChannelExtension[ChannelId.LndChannelId]] = {
    val hash = paymentHash.value.toArray

    SQL"""
         SELECT
           cer.payment_hash, cer.paying_currency, cer.channel_id, cer.fee, cer.seconds, cer.requested_at,
           cefp.paid_at
         FROM channel_extension_requests cer
         LEFT JOIN channel_extension_fee_payments cefp USING(payment_hash, paying_currency)
         WHERE cer.payment_hash = $hash AND cer.paying_currency = ${payingCurrency.entryName}::CURRENCY_TYPE 
       """.as(ChannelParsers.channelExtensionParser.singleOpt)
  }

  def findConnextChannelExtension(paymentHash: PaymentRHash, payingCurrency: Currency)(implicit
      conn: Connection
  ): Option[ChannelExtension[ChannelId.ConnextChannelId]] = {
    val hash = paymentHash.value.toArray

    SQL"""
         SELECT
           cer.payment_hash, cer.paying_currency, cer.connext_channel_id, cer.fee, cer.seconds, cer.requested_at,
           cefp.paid_at
         FROM connext_channel_extension_requests cer
         LEFT JOIN connext_channel_extension_fee_payments cefp USING(payment_hash, paying_currency)
         WHERE cer.payment_hash = $hash AND cer.paying_currency = ${payingCurrency.entryName}::CURRENCY_TYPE 
       """.as(ChannelParsers.connextChannelExtensionParser.singleOpt)
  }

  def findChannel(outPoint: LndOutpoint)(implicit conn: Connection): Option[Channel.LndChannel] = {
    val fundingTransaction = outPoint.txid.bigEndianBytes.toArray
    val outputIndex = outPoint.index

    SQL"""
        SELECT
          channel_id, payment_hash, public_key, funding_transaction, output_index, created_at, expires_at,
          channel_status, closing_type, closed_by, closed_on, paying_currency, client_public_key_id
        FROM channels
        WHERE funding_transaction = $fundingTransaction
          AND output_index = $outputIndex
     """.as(ChannelParsers.channelParser.singleOpt)
  }

  def findChannelFeePayment(outPoint: LndOutpoint)(implicit conn: Connection): Option[ChannelFeePayment] = {
    val fundingTransaction = outPoint.txid.bigEndianBytes.toArray
    val outputIndex = outPoint.index

    SQL"""
        SELECT
          fp.currency, fp.paying_currency, fp.capacity, fp.life_time_seconds, fp.fee
        FROM channels AS c
        JOIN channel_fee_payments AS fp USING (payment_hash, paying_currency)
        WHERE c.funding_transaction = $fundingTransaction
          AND c.output_index = $outputIndex
     """.as(ChannelParsers.channelPaymentParser.singleOpt)
  }

  def createCloseExpiredChannelRequest(channelId: ChannelId, active: Boolean, requestedOn: Instant)(implicit
      conn: Connection
  ): Unit = {
    try {
      SQL"""
          INSERT INTO close_expired_channel_requests
            (channel_id, active, requested_on)
          VALUES(
            ${channelId.toString}::UUID,
            $active,
            $requestedOn
          )
         """
        .execute()

      ()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.CloseExpiredChannelRequestPK) =>
        throw new PSQLException(s"close expired channel request for $channelId already exist", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.CloseExpiredChannelRequestChannelFK) =>
        throw new PSQLException(s"channel $channelId not found", PSQLState.DATA_ERROR)
    }
  }

  def findConfirmingConnextChannels()(implicit conn: Connection): List[ConnextChannel] = {
    SQL"""
        SELECT
          c.channel_address, c.transaction_hash, fp.currency
        FROM connext_channels c
        INNER JOIN channel_fee_payments fp USING(payment_hash, paying_currency)
        WHERE status = 'CONFIRMING'::CONNEXT_CHANNEL_STATUS
     """.as(ChannelParsers.connextPendingChannelParser.*)
  }

  def createConnextChannelContractDeploymentFee(
      fee: ConnextChannelContractDeploymentFee
  )(implicit conn: Connection): Unit = {
    try {
      val _ = SQL"""
        INSERT INTO connext_channel_contract_deployment_fees(
          transaction_hash,
          client_id,
          amount,
          created_at
        ) VALUES (
          ${fee.transactionHash},
          ${fee.clientId.uuid.toString}::UUID,
          ${fee.amount.value(18)}::SATOSHIS_TYPE,
          ${fee.createdAt}
        )
        """
        .execute()
    } catch {
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelContractDeploymentFeesPK) =>
        throw new PSQLException(s"transaction ${fee.transactionHash} already registered", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelContractDeploymentFeesClientsFK) =>
        throw new PSQLException(s"client ${fee.clientId} not found", PSQLState.DATA_ERROR)
      case e: PSQLException if violatesConstraint(e, Constraints.connextChannelContractDeploymentFeesClientIdUnique) =>
        throw new PSQLException(s"client ${fee.clientId} has already paid the fee", PSQLState.DATA_ERROR)
    }
  }

  def findConnextChannelContractDeploymentFee(
      clientId: ClientId
  )(implicit conn: Connection): Option[ConnextChannelContractDeploymentFee] = {
    SQL"""
        SELECT
          transaction_hash, client_id, amount, created_at
        FROM connext_channel_contract_deployment_fees
        WHERE client_id = ${clientId.uuid}::UUID
        """
      .as(ChannelParsers.connextChannelContractDeploymentFeeParser.singleOpt)
  }

  private def violatesConstraint(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
