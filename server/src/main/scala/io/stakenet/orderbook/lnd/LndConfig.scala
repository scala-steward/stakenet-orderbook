package io.stakenet.orderbook.lnd

// TODO: remove channel attributes ?
case class LndConfig(
    host: String,
    port: Int,
    tlsCertificateFile: String,
    macaroonFile: String,
    publicKey: String,
    channelIpAddress: String,
    channelPort: Int,
    channelMinSize: Long,
    maxSatPerByte: Long,
    invoiceUsdLimit: BigDecimal
)
