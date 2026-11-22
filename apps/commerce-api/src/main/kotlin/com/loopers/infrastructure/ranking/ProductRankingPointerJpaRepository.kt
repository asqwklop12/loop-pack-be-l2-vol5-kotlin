package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingPointer
import com.loopers.domain.ranking.ProductRankingPointerStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankingPointerJpaRepository : JpaRepository<ProductRankingPointer, Long> {
    fun findFirstBySnapshotIdStartingWithAndStatusAndDeletedAtIsNullOrderBySnapshotIdDesc(
        snapshotIdPrefix: String,
        status: ProductRankingPointerStatus,
    ): ProductRankingPointer?
}
