package com.loopers.application.payment

import com.loopers.domain.payment.PaymentStatus
import java.time.Instant

data class PaymentCallback(
    val transactionKey: String,
    val orderId: Long,
    val status: PaymentStatus,
    val declineCode: String? = null,
    val declineReason: String? = null,
    val settledAt: Instant? = null,
)
