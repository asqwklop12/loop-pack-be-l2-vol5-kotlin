package com.loopers.domain.point

interface PointRepository {
    fun save(pointBalance: PointBalance): PointBalance
    fun findByUserId(userId: Long): PointBalance?
}
