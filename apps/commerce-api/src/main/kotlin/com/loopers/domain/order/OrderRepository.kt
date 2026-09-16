package com.loopers.domain.order

interface OrderRepository {
    fun save(order: Order): Order
    fun find(id: Long): Order?
    fun findAllByUserId(userId: Long): List<Order>
}
