package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Order?
    fun findAllByUserIdAndDeletedAtIsNull(userId: Long): List<Order>
    fun findAllByDeletedAtIsNull(): List<Order>
}
