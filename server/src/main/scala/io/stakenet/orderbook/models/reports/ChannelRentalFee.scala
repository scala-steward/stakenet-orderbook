package io.stakenet.orderbook.models.reports

import java.time.Instant

import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.lnd.{LndTxid, PaymentRHash}

case class ChannelRentalFee(
    paymentHash: PaymentRHash,
    payingCurrency: Currency,
    rentedCurrency: Currency,
    feeAmount: Satoshis,
    capacity: Satoshis,
    fundingTransaction: LndTxid,
    fundingTransactionFee: Satoshis,
    closingTransaction: LndTxid,
    closingTransaction_fee: Satoshis,
    createdAt: Instant,
    lifeTimeSeconds: Long
)
