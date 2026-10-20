package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findAllByOrderId(orderId: Long): List<Payment>
}
