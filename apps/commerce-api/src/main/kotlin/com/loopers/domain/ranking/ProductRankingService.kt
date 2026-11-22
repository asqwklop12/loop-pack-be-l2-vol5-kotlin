package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime

@Component
class ProductRankingService(
    private val productRankingRepository: ProductRankingRepository,
    private val productRankingPointerRepository: ProductRankingPointerRepository,
) {
    @Transactional(readOnly = true)
    fun findPublishedPointer(periodStart: LocalDate): ProductRankingPointer? =
        productRankingPointerRepository.findLatestPublished(ProductRankingPointer.weekIdOf(periodStart))

    @Transactional(readOnly = true)
    fun findFirstWrittenAt(snapshotId: String): ZonedDateTime? =
        productRankingRepository.findFirstWrittenAt(snapshotId)

    @Transactional(readOnly = true)
    fun getPage(snapshotId: String, page: Int, size: Int): List<ProductRanking> =
        productRankingRepository.findPage(snapshotId, page, size)
}
