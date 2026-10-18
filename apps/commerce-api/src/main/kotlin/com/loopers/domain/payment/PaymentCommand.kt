package com.loopers.domain.payment

import com.loopers.domain.shared.Money

data class PaymentCommand(
    val userId: Long,
    val orderId: Long,
    val amount: Money,
    val method: PaymentMethod,
    val card: CardInfo? = null,
)
