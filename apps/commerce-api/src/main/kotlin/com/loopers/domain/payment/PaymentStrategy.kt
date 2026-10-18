package com.loopers.domain.payment

interface PaymentStrategy {
    fun supports(method: PaymentMethod): Boolean

    fun pay(command: PaymentCommand): PaymentResult
}
