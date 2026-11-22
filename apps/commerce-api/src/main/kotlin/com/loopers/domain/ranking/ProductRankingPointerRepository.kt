package com.loopers.domain.ranking

interface ProductRankingPointerRepository {
    fun findLatestPublished(weekId: String): ProductRankingPointer?
}
