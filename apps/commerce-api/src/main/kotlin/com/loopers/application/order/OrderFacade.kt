package com.loopers.application.order

import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
) {
    fun create(userId: Long, lines: List<OrderCommand.Line>): OrderInfo =
        OrderInfo.from(orderService.create(userId, lines))

    fun confirm(userId: Long, orderId: Long): OrderInfo =
        OrderInfo.from(orderService.confirm(userId, orderId))

    fun cancel(userId: Long, orderId: Long): OrderInfo =
        OrderInfo.from(orderService.cancel(userId, orderId))

    fun get(userId: Long, orderId: Long): OrderInfo =
        OrderInfo.from(orderService.get(userId, orderId))

    fun getAll(userId: Long): List<OrderInfo> =
        orderService.getAll(userId).map { OrderInfo.from(it) }

    fun getAnyOrder(orderId: Long): OrderInfo = OrderInfo.from(orderService.getAnyOrder(orderId))

    fun getAllOrders(): List<OrderInfo> = orderService.getAllOrders().map { OrderInfo.from(it) }
}
