package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByPaymentKey(paymentKey: String): Payment?

    fun findAllByOrderIdOrderByIdAsc(orderId: Long): List<Payment>
}
