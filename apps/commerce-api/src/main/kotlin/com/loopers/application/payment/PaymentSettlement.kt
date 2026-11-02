package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.StockReservationService
import org.springframework.stereotype.Component

@Component
class PaymentSettlement(
    private val stockReservationService: StockReservationService,
) {
    fun reflect(payment: Payment, status: PaymentStatus) {
        when (status) {
            PaymentStatus.SUCCESS -> succeed(payment)
            PaymentStatus.FAILED -> decline(payment)
            PaymentStatus.PENDING -> Unit
        }
    }

    private fun succeed(payment: Payment) {
        payment.markSuccess()
        stockReservationService.commit(payment.orderId)
    }

    private fun decline(payment: Payment) {
        payment.markFailed()
        stockReservationService.release(payment.orderId)
    }
}
