package com.loopers.application.ranking

import com.loopers.domain.product.ProductService
import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Component
class RankingFacade(
    private val productRankingService: ProductRankingService,
    private val productService: ProductService,
) {
    fun getWeekly(periodStart: LocalDate?, limit: Int?): WeeklyRankingInfo {
        val size = limit ?: DEFAULT_LIMIT
        if (size !in 1..MAX_LIMIT) {
            throw CoreException(ErrorType.BAD_REQUEST, "limit 은 1 이상 $MAX_LIMIT 이하여야 합니다. [limit = $size]")
        }
        val monday = (periodStart ?: LocalDate.now()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val inputFrozenAt = productRankingService.findFirstWrittenAt(monday)
            ?: return WeeklyRankingInfo.notPublished(monday)

        val firstPage = productRankingService.getPage(monday, 0, size)
        return WeeklyRankingInfo.published(
            periodStart = monday,
            snapshotId = firstPage.first().snapshotId,
            inputFrozenAt = inputFrozenAt,
            items = rankingsOfExistingProducts(monday, firstPage, size).map { WeeklyRankingInfo.Item.from(it) },
        )
    }

    private fun rankingsOfExistingProducts(
        monday: LocalDate,
        firstPage: List<ProductRanking>,
        size: Int,
    ): List<ProductRanking> {
        val collected = mutableListOf<ProductRanking>()
        var page = 0
        var rows = firstPage
        while (rows.isNotEmpty() && collected.size < size) {
            val existing = productService.getAllByIds(rows.map { it.productId }).map { it.id }.toSet()
            collected += rows.filter { it.productId in existing }
            if (rows.size < size) break
            rows = productRankingService.getPage(monday, ++page, size)
        }
        return collected.take(size)
    }

    companion object {
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 100
    }
}
