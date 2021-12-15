package io.stakenet.orderbook.connext

case class ConnextConfig(
    host: String,
    port: String,
    publicIdentifier: String,
    chainId: String,
    assetId: String,
    signerAddress: String,
    withdrawAddress: String
)
