package com.loopers.pg

import java.math.BigInteger
import java.time.Instant

object PgV1Dto {
    data class PaymentRequest(
        val orderId: Long,
        val amount: BigInteger,
        val cardType: CardType,
        val cardNo: String,
    )

    data class PaymentResponse(
        val transactionKey: String,
        val orderId: Long,
        val amount: BigInteger,
        val cardType: CardType,
        val maskedCardNo: String,
        val status: PgStatus,
        val requestedAt: Instant,
        val settledAt: Instant?,
        val declineCode: String?,
        val declineReason: String?,
    ) {
        companion object {
            fun from(transaction: PgTransaction) = PaymentResponse(
                transactionKey = transaction.transactionKey,
                orderId = transaction.orderId,
                amount = transaction.amount,
                cardType = transaction.cardType,
                maskedCardNo = transaction.maskedCardNo,
                status = transaction.status,
                requestedAt = transaction.requestedAt,
                settledAt = transaction.settledAt,
                declineCode = transaction.declineReason?.name,
                declineReason = transaction.declineReason?.message,
            )
        }
    }
}
