package com.loopers.pg

import java.math.BigInteger
import java.time.Instant

enum class PgStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

data class PgTransaction(
    val transactionKey: String,
    val orderId: Long,
    val amount: BigInteger,
    val status: PgStatus,
    val requestedAt: Instant,
    val settledAt: Instant? = null,
) {
    val isFinal: Boolean
        get() = status != PgStatus.PENDING

    fun settle(next: PgStatus, at: Instant): PgTransaction =
        if (isFinal) this else copy(status = next, settledAt = at)
}
