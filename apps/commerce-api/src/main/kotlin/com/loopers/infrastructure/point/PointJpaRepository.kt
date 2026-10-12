package com.loopers.infrastructure.point

import com.loopers.domain.point.PointBalance
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface PointJpaRepository : JpaRepository<PointBalance, Long> {
    fun findByUserId(userId: Long): PointBalance?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PointBalance p where p.userId = :userId")
    fun findByUserIdForUpdate(userId: Long): PointBalance?
}
