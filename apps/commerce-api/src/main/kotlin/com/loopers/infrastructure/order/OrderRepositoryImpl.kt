package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Component

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order = orderJpaRepository.save(order)

    override fun find(id: Long): Order? = orderJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findAllByUserId(userId: Long): List<Order> =
        orderJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId)

    override fun findAll(): List<Order> = orderJpaRepository.findAllByDeletedAtIsNull()
}
