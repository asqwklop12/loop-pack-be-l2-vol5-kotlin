package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
    private val likeQueryDslRepository: LikeQueryDslRepository,
) : LikeRepository {
    override fun save(like: Like): Like = likeJpaRepository.save(like)

    override fun find(userId: Long, productId: Long): Like? =
        likeJpaRepository.findByUserIdAndProductId(userId, productId)

    override fun delete(like: Like) = likeJpaRepository.delete(like)

    override fun countByProductId(productId: Long): Long = likeJpaRepository.countByProductId(productId)

    override fun findAllByUserId(userId: Long): List<Like> = likeJpaRepository.findAllByUserId(userId)

    override fun deleteAllByProductId(productId: Long) = likeJpaRepository.deleteAllByProductId(productId)

    override fun countByProductIds(productIds: List<Long>): Map<Long, Long> =
        likeQueryDslRepository.countByProductIds(productIds)
}
