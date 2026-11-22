package com.loopers.domain.ranking

import java.time.ZonedDateTime

interface ProductRankingRepository {
    fun findFirstWrittenAt(snapshotId: String): ZonedDateTime?
    fun findPage(snapshotId: String, page: Int, size: Int): List<ProductRanking>
}
