package com.loopers.domain.order

import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val productService: ProductService,
    private val pointService: PointService,
) {
    /**
     * 주문서만 만든다. 재고도 포인트도 건드리지 않는다. 둘 다 확정에서 일어난다.
     * 단가는 이 시점의 상품 가격으로 고정한다. 나중에는 알아낼 수 없는 값이다.
     */
    @Transactional
    fun create(userId: Long, commands: List<OrderCommand.Line>): Order {
        val lines = commands.map { command ->
            val product = productService.getSellable(command.productId)

            OrderLine(productId = product.id, quantity = command.quantity, unitPrice = product.price)
        }

        return orderRepository.save(Order(userId = userId, lines = lines))
    }

    /**
     * 주문의 소유자가 아니면 없는 주문과 같은 응답을 준다. 주문의 존재가 새어나가지 않아야 한다.
     */
    @Transactional(readOnly = true)
    fun get(userId: Long, orderId: Long): Order {
        return orderRepository.find(orderId)
            ?.takeIf { it.isOwnedBy(userId) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $orderId] 주문을 찾을 수 없습니다.")
    }

    @Transactional(readOnly = true)
    fun getAll(userId: Long): List<Order> = orderRepository.findAllByUserId(userId)

    /**
     * 관리자 조회. 소유자를 가리지 않는다.
     */
    @Transactional(readOnly = true)
    fun getAnyOrder(orderId: Long): Order {
        return orderRepository.find(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $orderId] 주문을 찾을 수 없습니다.")
    }

    @Transactional(readOnly = true)
    fun getAllOrders(): List<Order> = orderRepository.findAll()

    /**
     * 되돌리기 싼 것부터 차감한다. 재고가 먼저고 결제가 마지막이다.
     * 어느 단계에서 실패해도 같은 트랜잭션 안이라 앞 단계가 되돌아간다.
     *
     * 상품은 **여기서 다시 확인한다.** 주문서를 만든 뒤 상품이 내려갈 수 있다.
     */
    @Transactional
    fun confirm(userId: Long, orderId: Long): Order {
        val order = get(userId, orderId)
        order.requireDraft()

        order.lines.forEach { line ->
            val product = productService.getSellable(line.productId)
            productService.decreaseStock(product, line.quantity)
        }
        pointService.pay(userId, order.totalAmount)
        order.confirm(order.totalAmount)

        return orderRepository.save(order)
    }

    /**
     * 재고와 포인트는 확정에서만 차감하므로 확정된 주문만 복원한다.
     * 확정 전 취소는 잡은 것이 없어 상태만 바꾼다.
     */
    @Transactional
    fun cancel(userId: Long, orderId: Long): Order {
        val order = get(userId, orderId)
        val confirmed = order.isConfirmed()
        order.cancel()

        if (confirmed) {
            order.paidAmount?.let { pointService.refund(userId, it) }
            order.lines.forEach { line ->
                productService.restoreStock(line.productId, line.quantity)
            }
        }

        return orderRepository.save(order)
    }
}
