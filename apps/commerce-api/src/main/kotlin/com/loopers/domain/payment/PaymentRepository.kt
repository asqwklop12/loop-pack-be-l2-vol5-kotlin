package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun find(id: Long): Payment?

    fun findByPaymentKey(paymentKey: String): Payment?

    fun findAllByOrderId(orderId: Long): List<Payment>
}
