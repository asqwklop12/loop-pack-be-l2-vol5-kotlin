package com.loopers.domain.payment

import com.loopers.domain.shared.Money

interface PaymentGateway {
    fun request(orderId: Long, amount: Money, card: CardInfo): PaymentTransaction

    fun find(transactionKey: String): PaymentTransaction?

    fun findByOrderId(orderId: Long): PaymentTransaction?
}
