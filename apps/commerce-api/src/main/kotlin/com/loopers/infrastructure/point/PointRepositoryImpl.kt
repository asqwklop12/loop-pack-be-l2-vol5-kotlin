package com.loopers.infrastructure.point

import com.loopers.domain.point.PointBalance
import com.loopers.domain.point.PointRepository
import org.springframework.stereotype.Component

@Component
class PointRepositoryImpl(
    private val pointJpaRepository: PointJpaRepository,
) : PointRepository {
    override fun save(pointBalance: PointBalance): PointBalance = pointJpaRepository.save(pointBalance)

    override fun findByUserId(userId: Long): PointBalance? = pointJpaRepository.findByUserId(userId)
}
