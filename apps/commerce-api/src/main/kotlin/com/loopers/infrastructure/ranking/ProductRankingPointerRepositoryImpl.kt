package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingPointer
import com.loopers.domain.ranking.ProductRankingPointerRepository
import com.loopers.domain.ranking.ProductRankingPointerStatus
import org.springframework.stereotype.Component

@Component
class ProductRankingPointerRepositoryImpl(
    private val productRankingPointerJpaRepository: ProductRankingPointerJpaRepository,
) : ProductRankingPointerRepository {
    override fun findLatestPublished(weekId: String): ProductRankingPointer? =
        productRankingPointerJpaRepository.findFirstBySnapshotIdStartingWithAndStatusAndDeletedAtIsNullOrderBySnapshotIdDesc(
            "${weekId}_r",
            ProductRankingPointerStatus.PUBLISHED,
        )
}
