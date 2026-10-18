package com.loopers.domain.product

interface ProductStatsRepository {
    fun replaceAllFromLikes(): Int

    fun countsOf(productIds: List<Long>): Map<Long, Long>
}
