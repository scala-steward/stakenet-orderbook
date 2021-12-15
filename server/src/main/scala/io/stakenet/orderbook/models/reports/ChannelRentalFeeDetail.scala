package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelRentalFeeDetail(
    paymentHash: PaymentRHash,
    currency: Currency,
    rentingFee: Satoshis,
    transactioFee: Satoshis,
    forceClosingFee: Satoshis
)
