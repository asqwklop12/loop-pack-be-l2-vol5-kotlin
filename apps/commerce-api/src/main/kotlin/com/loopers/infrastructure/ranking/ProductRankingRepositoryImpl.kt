package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class ProductRankingRepositoryImpl(
    private val productRankingJpaRepository: ProductRankingJpaRepository,
) : ProductRankingRepository {
    override fun findFirstWrittenAt(snapshotId: String): ZonedDateTime? =
        productRankingJpaRepository.findFirstCreatedAt(snapshotId)

    override fun findPage(snapshotId: String, page: Int, size: Int): List<ProductRanking> =
        productRankingJpaRepository.findAllBySnapshotIdAndDeletedAtIsNullOrderByRankingAscProductIdAsc(
            snapshotId,
            PageRequest.of(page, size),
        )
}
