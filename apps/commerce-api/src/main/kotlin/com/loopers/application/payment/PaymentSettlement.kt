package com.loopers.application.payment

import com.loopers.domain.order.OrderConfirmedEvent
import com.loopers.domain.order.OrderEventPublisher
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.StockReservation
import com.loopers.domain.product.StockReservationService
import org.springframework.stereotype.Component

@Component
class PaymentSettlement(
    private val stockReservationService: StockReservationService,
    private val orderEventPublisher: OrderEventPublisher,
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
        val reserved = stockReservationService.commit(payment.orderId)

        orderEventPublisher.publish(confirmedEvent(payment.orderId, reserved))
    }

    private fun decline(payment: Payment) {
        payment.markFailed()
        stockReservationService.release(payment.orderId)
    }

    private fun confirmedEvent(orderId: Long, reserved: List<StockReservation>) = OrderConfirmedEvent(
        orderId = orderId,
        items = reserved.map { OrderConfirmedEvent.Item(productId = it.productId, quantity = it.quantity) },
    )
}
