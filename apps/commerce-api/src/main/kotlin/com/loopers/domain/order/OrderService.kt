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
     * 주문서를 만든다. 재고·포인트를 차감하지 않는다.
     * 단가는 이 시점의 상품 가격으로 고정한다.
     */
    @Transactional
    fun create(userId: Long, lines: List<OrderCommand.Line>): Order {
        val items = lines.map { line ->
            val product = productService.get(line.productId)
            OrderItem(productId = product.id, quantity = line.quantity, unitPrice = product.price)
        }

        return orderRepository.save(Order(userId = userId, items = items))
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
     * 되돌리기 싼 것부터 차감한다. 재고가 먼저고 결제가 마지막이다.
     * 어느 단계에서 실패해도 같은 트랜잭션 안이라 앞 단계가 되돌아간다.
     */
    @Transactional
    fun confirm(userId: Long, orderId: Long): Order {
        val order = get(userId, orderId)

        order.items.forEach { item ->
            productService.get(item.productId).decreaseStock(item.quantity)
        }
        pointService.pay(userId, order.totalAmount)
        order.confirm(order.totalAmount)

        return orderRepository.save(order)
    }
}
