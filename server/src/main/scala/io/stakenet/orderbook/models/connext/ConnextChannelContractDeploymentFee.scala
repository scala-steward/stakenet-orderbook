package io.stakenet.orderbook.models.connext

import java.time.Instant

import io.stakenet.orderbook.models.Satoshis
import io.stakenet.orderbook.models.clients.ClientId

case class ConnextChannelContractDeploymentFee(
    transactionHash: String,
    clientId: ClientId,
    amount: Satoshis,
    createdAt: Instant
)
