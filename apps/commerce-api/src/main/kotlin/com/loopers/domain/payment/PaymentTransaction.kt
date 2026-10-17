package com.loopers.domain.payment

import com.loopers.domain.shared.Money

data class PaymentTransaction(
    val transactionKey: String,
    val orderId: Long,
    val amount: Money,
    val maskedCardNo: String,
    val status: PaymentStatus,
    val declineReason: String? = null,
) {
    val isFinal: Boolean
        get() = status != PaymentStatus.PENDING
}
