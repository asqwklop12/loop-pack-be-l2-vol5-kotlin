package com.loopers.application.order

import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderService
import com.loopers.application.payment.PaymentProcessor
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 주문·상품·포인트를 조합한다. **도메인끼리 직접 부르지 않고 여기서 엮는다.**
 *
 * 그래서 여러 도메인을 건드리는 흐름은 트랜잭션 경계도 여기다. 중간에 실패하면 앞 단계가
 * 함께 되돌아가야 하는데, 경계가 도메인 서비스에 있으면 그 범위를 한 도메인이 정하게 된다.
 */
@Component
class OrderFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val pointService: PointService,
    private val paymentProcessor: PaymentProcessor,
    private val paymentRepository: PaymentRepository,
) {
    /**
     * 주문서만 만든다. 단가는 이 시점의 상품 가격으로 고정한다. 나중에는 알아낼 수 없는 값이다.
     */
    @Transactional
    fun create(userId: Long, lines: List<OrderCommand.Line>): OrderInfo {
        val orderLines = lines.map { line ->
            val product = productService.getSellable(line.productId)

            OrderLine(productId = product.id, quantity = line.quantity, unitPrice = product.price)
        }

        return OrderInfo.from(orderService.create(userId, orderLines))
    }

    /**
     * 되돌리기 싼 것부터 차감한다. 재고가 먼저고 결제가 마지막이다.
     * 어느 단계에서 실패해도 같은 트랜잭션 안이라 앞 단계가 되돌아간다.
     *
     * 상품은 **여기서 다시 확인한다.** 주문서를 만든 뒤 상품이 내려갈 수 있다.
     */
    @Transactional
    fun confirm(
        userId: Long,
        orderId: Long,
        method: PaymentMethod = PaymentMethod.POINT,
        card: CardInfo? = null,
    ): OrderInfo {
        val order = orderService.get(userId, orderId)
        order.requireDraft()

        order.lines.forEach { line ->
            val product = productService.getSellable(line.productId)
            productService.decreaseStock(product, line.quantity)
        }

        val payment = paymentRepository.save(Payment(orderId = order.id, amount = order.totalAmount))

        val result = paymentProcessor.pay(
            PaymentCommand(
                userId = userId,
                orderId = order.id,
                amount = order.totalAmount,
                method = method,
                card = card,
            ),
        )

        when (result.status) {
            PaymentStatus.SUCCESS -> payment.markSuccess()
            PaymentStatus.FAILED -> payment.markFailed()
            PaymentStatus.PENDING -> Unit
        }

        return OrderInfo.from(orderService.confirm(order))
    }

    /**
     * 재고와 포인트는 확정에서만 차감하므로 확정된 주문만 복원한다.
     * 확정 전 취소는 잡은 것이 없어 상태만 바꾼다.
     */
    @Transactional
    fun cancel(userId: Long, orderId: Long): OrderInfo {
        val order = orderService.get(userId, orderId)
        val confirmed = order.isConfirmed()
        val canceled = orderService.cancel(order)

        if (confirmed) {
            canceled.paidAmount?.let { pointService.refund(userId, it) }
            canceled.lines.forEach { line ->
                productService.restoreStock(line.productId, line.quantity)
            }
        }

        return OrderInfo.from(canceled)
    }

    fun get(userId: Long, orderId: Long): OrderInfo =
        OrderInfo.from(orderService.get(userId, orderId))

    fun getAll(userId: Long): List<OrderInfo> =
        orderService.getAll(userId).map { OrderInfo.from(it) }

    fun getAnyOrder(orderId: Long): OrderInfo = OrderInfo.from(orderService.getAnyOrder(orderId))

    fun getAllOrders(): List<OrderInfo> = orderService.getAllOrders().map { OrderInfo.from(it) }
}
