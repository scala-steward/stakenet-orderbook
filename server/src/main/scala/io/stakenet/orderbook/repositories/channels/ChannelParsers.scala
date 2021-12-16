package io.stakenet.orderbook.repositories.channels

import anorm.{Column, Macro, RowParser}
import io.stakenet.orderbook.models.ChannelIdentifier.ConnextChannelAddress
import io.stakenet.orderbook.models.connext.{ConnextChannel, ConnextChannelContractDeploymentFee}
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Channel, ChannelExtension, ChannelId, ConnextChannelStatus}
import io.stakenet.orderbook.repositories.CommonParsers._

private[channels] object ChannelParsers {

  implicit val channelIdColumn: Column[ChannelId.LndChannelId] = Column.columnToUUID.map(ChannelId.LndChannelId.apply)

  implicit val connextChannelIdColumn: Column[ChannelId.ConnextChannelId] =
    Column.columnToUUID.map(ChannelId.ConnextChannelId.apply)
  implicit val channelStatusColumn: Column[ChannelStatus] = enumColumn(ChannelStatus.withNameInsensitiveOption)

  implicit val connextChannelStatusColumn: Column[ConnextChannelStatus] =
    enumColumn(ConnextChannelStatus.withNameInsensitiveOption)
  implicit val fundingTxidColumn: Column[LndTxid] = Column.columnToByteArray.map(x => LndTxid(x.toVector))

  implicit val channelAddressColumn: Column[ConnextChannelAddress] = Column.columnToString
    .map(ConnextChannelAddress.apply)
    .map(_.getOrElse(throw new RuntimeException("Invalid connext channel address retrieved from the database")))

  val channelPaymentParser: RowParser[ChannelFeePayment] = Macro.parser[ChannelFeePayment](
    "currency",
    "paying_currency",
    "capacity",
    "life_time_seconds",
    "fee"
  )

  val channelParser: RowParser[Channel.LndChannel] = Macro.parser[Channel.LndChannel](
    "channel_id",
    "payment_hash",
    "paying_currency",
    "public_key",
    "client_public_key_id",
    "funding_transaction",
    "output_index",
    "created_at",
    "expires_at",
    "channel_status",
    "closing_type",
    "closed_by",
    "closed_on"
  )

  val connextChannelParser: RowParser[Channel.ConnextChannel] = Macro.parser[Channel.ConnextChannel](
    "connext_channel_id",
    "payment_hash",
    "paying_currency",
    "public_identifier",
    "client_public_identifier_id",
    "channel_address",
    "status",
    "created_at",
    "expires_at"
  )

  val lndChannelParser: RowParser[LndChannel] = Macro.parser[LndChannel](
    "channel_id",
    "currency",
    "funding_transaction",
    "output_index",
    "life_time_seconds",
    "channel_status"
  )

  val channelExtensionParser: RowParser[ChannelExtension[ChannelId.LndChannelId]] =
    Macro.parser[ChannelExtension[ChannelId.LndChannelId]](
      "payment_hash",
      "paying_currency",
      "channel_id",
      "fee",
      "seconds",
      "requested_at",
      "paid_at"
    )

  val connextChannelExtensionParser: RowParser[ChannelExtension[ChannelId.ConnextChannelId]] =
    Macro.parser[ChannelExtension[ChannelId.ConnextChannelId]](
      "payment_hash",
      "paying_currency",
      "connext_channel_id",
      "fee",
      "seconds",
      "requested_at",
      "paid_at"
    )

  val connextPendingChannelParser: RowParser[ConnextChannel] = Macro.parser[ConnextChannel](
    "channel_address",
    "transaction_hash",
    "currency"
  )

  val connextChannelContractDeploymentFeeParser: RowParser[ConnextChannelContractDeploymentFee] =
    Macro.parser[ConnextChannelContractDeploymentFee](
      "transaction_hash",
      "client_id",
      "amount",
      "created_at"
    )
}
