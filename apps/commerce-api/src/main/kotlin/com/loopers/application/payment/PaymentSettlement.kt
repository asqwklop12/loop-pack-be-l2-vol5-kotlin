package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component

@Component
class PaymentSettlement(
    private val orderService: OrderService,
    private val productService: ProductService,
) {
    fun reflect(payment: Payment, status: PaymentStatus) {
        when (status) {
            PaymentStatus.SUCCESS -> succeed(payment)
            PaymentStatus.FAILED -> payment.markFailed()
            PaymentStatus.PENDING -> Unit
        }
    }

    private fun succeed(payment: Payment) {
        payment.markSuccess()

        orderService.getAnyOrder(payment.orderId).lines.forEach { line ->
            val product = productService.getSellable(line.productId)
            productService.decreaseStock(product, line.quantity)
        }
    }
}
