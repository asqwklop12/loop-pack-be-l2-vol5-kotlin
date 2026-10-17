package com.loopers.pg

import java.math.BigInteger
import java.time.Instant

object PgV1Dto {
    data class PaymentRequest(
        val orderId: Long,
        val amount: BigInteger,
    )

    data class PaymentResponse(
        val transactionKey: String,
        val orderId: Long,
        val amount: BigInteger,
        val status: PgStatus,
        val requestedAt: Instant,
        val settledAt: Instant?,
    ) {
        companion object {
            fun from(transaction: PgTransaction) = PaymentResponse(
                transactionKey = transaction.transactionKey,
                orderId = transaction.orderId,
                amount = transaction.amount,
                status = transaction.status,
                requestedAt = transaction.requestedAt,
                settledAt = transaction.settledAt,
            )
        }
    }
}
