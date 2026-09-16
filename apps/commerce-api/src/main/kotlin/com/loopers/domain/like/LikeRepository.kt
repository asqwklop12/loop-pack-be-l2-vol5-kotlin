package com.loopers.domain.like

interface LikeRepository {
    fun save(like: Like): Like
    fun find(userId: Long, productId: Long): Like?
    fun delete(like: Like)
    fun countByProductId(productId: Long): Long
    fun findAllByUserId(userId: Long): List<Like>
}
