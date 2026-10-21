package com.loopers.domain.payment

interface PaymentStrategy {
    fun supports(method: PaymentMethod): Boolean

    fun require(command: PaymentCommand) = Unit

    fun pay(command: PaymentCommand): PaymentResult

    val external: Boolean
}
