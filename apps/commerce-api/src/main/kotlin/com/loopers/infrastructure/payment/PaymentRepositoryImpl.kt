package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun find(id: Long): Payment? = paymentJpaRepository.findById(id).orElse(null)

    override fun findByPaymentKey(paymentKey: String): Payment? =
        paymentJpaRepository.findByPaymentKey(paymentKey)

    override fun findAllByOrderId(orderId: Long): List<Payment> =
        paymentJpaRepository.findAllByOrderIdOrderByIdAsc(orderId)
}
