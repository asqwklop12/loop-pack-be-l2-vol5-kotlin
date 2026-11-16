package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime

@Component
class ProductRankingService(
    private val productRankingRepository: ProductRankingRepository,
) {
    @Transactional(readOnly = true)
    fun findFirstWrittenAt(periodStart: LocalDate): ZonedDateTime? =
        productRankingRepository.findFirstWrittenAt(periodStart)

    @Transactional(readOnly = true)
    fun getPage(periodStart: LocalDate, page: Int, size: Int): List<ProductRanking> =
        productRankingRepository.findPage(periodStart, page, size)
}
