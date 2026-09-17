package com.loopers.application.order

import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderService: OrderService,
) {
    @Transactional
    fun create(userId: Long, lines: List<OrderCommand.Line>): OrderInfo =
        OrderInfo.from(orderService.create(userId, lines))

    @Transactional
    fun confirm(userId: Long, orderId: Long): OrderInfo =
        OrderInfo.from(orderService.confirm(userId, orderId))

    @Transactional(readOnly = true)
    fun get(userId: Long, orderId: Long): OrderInfo =
        OrderInfo.from(orderService.get(userId, orderId))

    @Transactional(readOnly = true)
    fun getAll(userId: Long): List<OrderInfo> =
        orderService.getAll(userId).map { OrderInfo.from(it) }

    @Transactional(readOnly = true)
    fun getAnyOrder(orderId: Long): OrderInfo = OrderInfo.from(orderService.getAnyOrder(orderId))

    @Transactional(readOnly = true)
    fun getAllOrders(): List<OrderInfo> = orderService.getAllOrders().map { OrderInfo.from(it) }
}
