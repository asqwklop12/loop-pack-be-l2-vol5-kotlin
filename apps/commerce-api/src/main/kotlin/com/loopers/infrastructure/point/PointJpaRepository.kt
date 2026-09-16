package com.loopers.infrastructure.point

import com.loopers.domain.point.PointBalance
import org.springframework.data.jpa.repository.JpaRepository

interface PointJpaRepository : JpaRepository<PointBalance, Long> {
    fun findByUserId(userId: Long): PointBalance?
}
