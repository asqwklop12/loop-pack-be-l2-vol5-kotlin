package com.loopers.interfaces.api.payment

import com.loopers.domain.payment.PaymentStatus
import java.time.Instant

class PaymentV1Dto {
    data class CallbackRequest(
        val transactionKey: String,
        val orderId: Long,
        val status: PaymentStatus,
        val amount: String,
        val declineCode: String? = null,
        val declineReason: String? = null,
        val settledAt: Instant? = null,
    )
}
