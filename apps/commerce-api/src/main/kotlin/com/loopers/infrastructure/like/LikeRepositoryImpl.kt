package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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

    /**
     * 파생 삭제 쿼리는 트랜잭션을 요구한다. 저장소를 직접 부르는 경로에서도 동작하도록 여기에 둔다.
     */
    @Transactional
    override fun deleteAllByProductId(productId: Long) = likeJpaRepository.deleteAllByProductId(productId)

    override fun countByProductIds(productIds: List<Long>): Map<Long, Long> =
        likeQueryDslRepository.countByProductIds(productIds)
}
