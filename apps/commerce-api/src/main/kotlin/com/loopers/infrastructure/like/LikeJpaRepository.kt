package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<Like, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?
    fun countByProductId(productId: Long): Long
    fun findAllByUserId(userId: Long): List<Like>
}
