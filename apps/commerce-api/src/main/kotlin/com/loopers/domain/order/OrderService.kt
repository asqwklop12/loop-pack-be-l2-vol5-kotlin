package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
) {
    /**
     * 주문서만 만든다. 재고도 포인트도 건드리지 않는다. 둘 다 확정에서 일어난다.
     * 품목은 이미 만들어져 들어온다. 단가를 상품에서 가져오는 일은 다른 도메인이라 퍼사드가 한다.
     */
    @Transactional
    fun create(userId: Long, lines: List<OrderLine>): Order {
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
     * 금액을 고정하고 상태를 바꾼다. 재고 차감과 결제는 다른 도메인이라 퍼사드가 앞에서 한다.
     */
    @Transactional
    fun confirm(order: Order): Order {
        order.confirm(order.totalAmount)

        return orderRepository.save(order)
    }

    /**
     * 상태만 바꾼다. 재고와 포인트 복원은 퍼사드가 뒤에서 한다.
     */
    @Transactional
    fun cancel(order: Order): Order {
        order.cancel()

        return orderRepository.save(order)
    }
}
