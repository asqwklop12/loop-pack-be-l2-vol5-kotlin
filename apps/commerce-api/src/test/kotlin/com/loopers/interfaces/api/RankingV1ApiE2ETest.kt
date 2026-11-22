package com.loopers.interfaces.api

import com.loopers.application.ranking.WeeklyRankingStatus
import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingPointer
import com.loopers.domain.ranking.ProductRankingPointerStatus
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.ranking.ProductRankingJpaRepository
import com.loopers.infrastructure.ranking.ProductRankingPointerJpaRepository
import com.loopers.interfaces.api.ranking.RankingV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productRankingJpaRepository: ProductRankingJpaRepository,
    private val productRankingPointerJpaRepository: ProductRankingPointerJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val monday = LocalDate.parse("2026-11-09")
    private val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.WeeklyResponse>>() {}

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun get(query: String): ResponseEntity<ApiResponse<RankingV1Dto.WeeklyResponse>> =
        testRestTemplate.exchange("/api/v1/rankings/weekly$query", HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

    private fun savedProducts(count: Int): List<Product> {
        val brand = brandJpaRepository.save(Brand(name = "나이키코리아"))
        return (1..count).map {
            productJpaRepository.save(Product(brandId = brand.id, name = "상품$it", price = Money(1_000), stock = Stock(5)))
        }
    }

    private fun rank(product: Product, score: Long, ranking: Int, snapshotId: String = FIRST) {
        productRankingJpaRepository.save(
            ProductRanking(snapshotId = snapshotId, periodStart = monday, productId = product.id, score = score, ranking = ranking),
        )
    }

    private fun point(status: ProductRankingPointerStatus, productCount: Long? = null, snapshotId: String = FIRST) {
        productRankingPointerJpaRepository.save(ProductRankingPointer(snapshotId, status, productCount))
    }

    companion object {
        private const val FIRST = "2026_11_2_r1"
        private const val SECOND = "2026_11_2_r2"
    }

    @DisplayName("GET /api/v1/rankings/weekly")
    @Nested
    inner class GetWeekly {
        @DisplayName("공개된 주를 주면, 순위 순으로 limit 개를 반환한다.")
        @Test
        fun returnsRankingsInOrder_whenWeekIsPublished() {
            val (a, b, c) = savedProducts(3)
            rank(a, score = 9, ranking = 1)
            rank(b, score = 3, ranking = 2)
            rank(c, score = 3, ranking = 2)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 3)

            val response = get("?periodStart=2026-11-09&limit=2")

            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.PUBLISHED) },
                { assertThat(data?.periodStart).isEqualTo(monday) },
                { assertThat(data?.periodEnd).isEqualTo(LocalDate.parse("2026-11-15")) },
                { assertThat(data?.snapshotId).isEqualTo(FIRST) },
                { assertThat(data?.inputFrozenAt).isNotNull() },
                {
                    assertThat(data?.items?.map { Triple(it.productId, it.score, it.ranking) })
                        .containsExactly(Triple(a.id, 9L, 1), Triple(b.id, 3L, 2))
                },
            )
        }

        @DisplayName("월요일이 아닌 날짜를 주면, 그 주 월요일의 랭킹을 반환한다.")
        @Test
        fun returnsMondayRanking_whenPeriodStartIsNotMonday() {
            val (a) = savedProducts(1)
            rank(a, score = 9, ranking = 1)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 1)

            val data = get("?periodStart=2026-11-12").body?.data

            assertAll(
                { assertThat(data?.periodStart).isEqualTo(monday) },
                { assertThat(data?.items?.map { it.productId }).containsExactly(a.id) },
            )
        }

        @DisplayName("삭제된 상품은 빼고, 남은 상품의 순위는 그대로 둔다.")
        @Test
        fun excludesDeletedProducts_keepingRankingGaps() {
            val (a, b, c) = savedProducts(3)
            rank(a, score = 9, ranking = 1)
            rank(b, score = 5, ranking = 2)
            rank(c, score = 1, ranking = 3)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 3)
            productJpaRepository.save(b.apply { delete() })

            val data = get("?periodStart=2026-11-09&limit=2").body?.data

            assertThat(data?.items?.map { it.productId to it.ranking }).containsExactly(a.id to 1, c.id to 3)
        }

        @DisplayName("포인터가 없는 주를 주면, NOT_PUBLISHED 와 빈 목록을 반환한다.")
        @Test
        fun returnsNotPublished_whenWeekHasNoRanking() {
            val data = get("?periodStart=2026-11-16").body?.data

            assertAll(
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.NOT_PUBLISHED) },
                { assertThat(data?.periodStart).isEqualTo(LocalDate.parse("2026-11-16")) },
                { assertThat(data?.snapshotId).isNull() },
                { assertThat(data?.items).isEmpty() },
            )
        }

        @DisplayName("랭킹 행이 있어도 포인터가 없으면, NOT_PUBLISHED 와 빈 목록을 반환한다.")
        @Test
        fun returnsNotPublished_whenPointerIsMissing() {
            val (a) = savedProducts(1)
            rank(a, score = 9, ranking = 1)

            val data = get("?periodStart=2026-11-09").body?.data

            assertAll(
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.NOT_PUBLISHED) },
                { assertThat(data?.items).isEmpty() },
            )
        }

        @DisplayName("r1 을 쓰다 멈추거나 실패해 공개된 리비전이 없으면, 쓴 행이 있어도 NOT_PUBLISHED 와 빈 목록을 반환한다.")
        @ParameterizedTest
        @EnumSource(value = ProductRankingPointerStatus::class, names = ["IN_PROGRESS", "FAILED"])
        fun returnsNotPublished_whenFirstIsNotPublished(status: ProductRankingPointerStatus) {
            val (a, b) = savedProducts(2)
            rank(a, score = 9, ranking = 1)
            rank(b, score = 5, ranking = 2)
            point(status)

            val data = get("?periodStart=2026-11-09").body?.data

            assertAll(
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.NOT_PUBLISHED) },
                { assertThat(data?.snapshotId).isNull() },
                { assertThat(data?.items).isEmpty() },
            )
        }

        @DisplayName("r1 이 공개된 뒤 r2 를 쓰다 멈추거나 실패하면, r1 랭킹을 그대로 반환한다.")
        @ParameterizedTest
        @EnumSource(value = ProductRankingPointerStatus::class, names = ["IN_PROGRESS", "FAILED"])
        fun returnsFirst_whenSecondIsNotPublished(status: ProductRankingPointerStatus) {
            val (a, b) = savedProducts(2)
            rank(a, score = 9, ranking = 1)
            rank(b, score = 5, ranking = 2)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 2)
            rank(b, score = 15, ranking = 1, snapshotId = SECOND)
            point(status, snapshotId = SECOND)

            val data = get("?periodStart=2026-11-09").body?.data

            assertAll(
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.PUBLISHED) },
                { assertThat(data?.snapshotId).isEqualTo(FIRST) },
                { assertThat(data?.items?.map { Triple(it.productId, it.score, it.ranking) }).containsExactly(Triple(a.id, 9L, 1), Triple(b.id, 5L, 2)) },
            )
        }

        @DisplayName("r2 가 공개되면, 늦은 구매가 반영된 r2 랭킹을 반환한다.")
        @Test
        fun returnsSecond_whenSecondIsPublished() {
            val (a, b) = savedProducts(2)
            rank(a, score = 9, ranking = 1)
            rank(b, score = 5, ranking = 2)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 2)
            rank(b, score = 15, ranking = 1, snapshotId = SECOND)
            rank(a, score = 9, ranking = 2, snapshotId = SECOND)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 2, snapshotId = SECOND)

            val data = get("?periodStart=2026-11-09").body?.data

            assertAll(
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.PUBLISHED) },
                { assertThat(data?.snapshotId).isEqualTo(SECOND) },
                { assertThat(data?.items?.map { Triple(it.productId, it.score, it.ranking) }).containsExactly(Triple(b.id, 15L, 1), Triple(a.id, 9L, 2)) },
            )
        }

        @DisplayName("r2 가 공개된 뒤 r1 포인터가 남아 있거나 늦게 공개돼도, r2 랭킹을 반환한다.")
        @ParameterizedTest
        @EnumSource(value = ProductRankingPointerStatus::class, names = ["IN_PROGRESS", "FAILED", "PUBLISHED"])
        fun returnsSecond_whateverFirstPointerIs(status: ProductRankingPointerStatus) {
            val (a, b) = savedProducts(2)
            rank(a, score = 9, ranking = 1)
            point(status, productCount = if (status == ProductRankingPointerStatus.PUBLISHED) 1 else null)
            rank(b, score = 15, ranking = 1, snapshotId = SECOND)
            rank(a, score = 9, ranking = 2, snapshotId = SECOND)
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 2, snapshotId = SECOND)

            val data = get("?periodStart=2026-11-09").body?.data

            assertAll(
                { assertThat(data?.snapshotId).isEqualTo(SECOND) },
                { assertThat(data?.items?.map { it.productId }).containsExactly(b.id, a.id) },
            )
        }

        @DisplayName("공개됐지만 랭킹 행이 없는 주면, PUBLISHED 와 빈 목록을 반환한다.")
        @Test
        fun returnsPublishedEmpty_whenWeekHasNoPurchase() {
            point(ProductRankingPointerStatus.PUBLISHED, productCount = 0)

            val data = get("?periodStart=2026-11-09").body?.data

            assertAll(
                { assertThat(data?.status).isEqualTo(WeeklyRankingStatus.PUBLISHED) },
                { assertThat(data?.snapshotId).isEqualTo(FIRST) },
                { assertThat(data?.inputFrozenAt).isNull() },
                { assertThat(data?.items).isEmpty() },
            )
        }

        @DisplayName("periodStart 가 날짜 형식이 아니면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenPeriodStartIsNotDate() {
            assertThat(get("?periodStart=2026-13-40").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("limit 이 1 보다 작거나 100 보다 크면, 400 BAD_REQUEST 응답을 받는다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, 101])
        fun returnsBadRequest_whenLimitIsOutOfRange(limit: Int) {
            assertThat(get("?periodStart=2026-11-09&limit=$limit").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
