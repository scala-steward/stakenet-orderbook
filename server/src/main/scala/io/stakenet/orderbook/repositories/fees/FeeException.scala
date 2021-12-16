package io.stakenet.orderbook.repositories.fees

import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.fees.requests.{BurnFeeRequest, LinkFeeToOrderRequest}

object FeeException {

  case class FeeLocked(paymentHash: PaymentRHash, lockedForOrderId: OrderId)
      extends RuntimeException(
        s"The paid fee already locked by order = ${lockedForOrderId.value}, hash = $paymentHash"
      )

  case class OrderNotLinkedToFeeWhileBurning(orderId: OrderId)
      extends RuntimeException(s"Tried to burn fee for order = $orderId but there wasn't any")

  case class MissingFundsToLinkOrder(request: LinkFeeToOrderRequest, availableAmount: Satoshis)
      extends RuntimeException(
        "The max funds you can place for this payment hash is: %s, but %s received, hash = %s".format(
          availableAmount.toString(request.currency),
          request.amount.toString(request.currency),
          request.hash
        )
      )

  case class MissingFundsToBurnFee(request: BurnFeeRequest, availableAmount: Satoshis)
      extends RuntimeException(
        "The max funds you can burn for this payment hash is: %s, but %s received, orderId = %s".format(
          availableAmount.toString(request.currency),
          request.amount.toString(request.currency),
          request.orderId
        )
      )

  case class RefundedFeeNotFound(hash: PaymentRHash, currency: Currency)
      extends RuntimeException(
        s"Order with payment hash $hash for $currency does not exist"
      )

}
