package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductStatsLock
import com.loopers.domain.product.ProductStatsRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductStatsRefresherTest @Autowired constructor(
    private val productStatsRefresher: ProductStatsRefresher,
    private val productStatsRepository: ProductStatsRepository,
    private val likeService: LikeService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var productStatsLock: ProductStatsLock

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun savedProduct(): Product {
        val brand = brandJpaRepository.save(Brand(name = "테스트브랜드"))

        return productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(1_000), stock = Stock(5)),
        )
    }

    private fun holdingLock() {
        every { productStatsLock.tryAcquire() } returns true
        every { productStatsLock.release() } returns Unit
    }

    @DisplayName("집계할 때,")
    @Nested
    inner class Refresh {
        @DisplayName("좋아요 수가 상품별로 집계된다.")
        @Test
        fun countsPerProduct() {
            holdingLock()
            val popular = savedProduct()
            val quiet = savedProduct()
            listOf(1L, 2L, 3L).forEach { likeService.like(it, popular.id) }
            likeService.like(1L, quiet.id)

            productStatsRefresher.refresh()

            val counts = productStatsRepository.countsOf(listOf(popular.id, quiet.id))
            assertAll(
                { assertThat(counts[popular.id]).isEqualTo(3L) },
                { assertThat(counts[quiet.id]).isEqualTo(1L) },
            )
        }

        @DisplayName("좋아요가 없는 상품은 집계에 없다. 호출자가 0 으로 읽는다.")
        @Test
        fun skipsProductsWithoutLikes() {
            holdingLock()
            val lonely = savedProduct()

            productStatsRefresher.refresh()

            assertThat(productStatsRepository.countsOf(listOf(lonely.id))[lonely.id]).isNull()
        }

        /**
         * 좋아요 취소가 하드 삭제라, 갱신만 하면 전부 취소된 상품이 예전 값으로 남는다.
         * 지우고 다시 채우는 이유가 이것이다.
         */
        @DisplayName("지난 집계 이후 전부 취소되면 그 상품은 집계에서 사라진다.")
        @Test
        fun reflectsCancellation() {
            holdingLock()
            val product = savedProduct()
            likeService.like(1L, product.id)
            productStatsRefresher.refresh()
            assertThat(productStatsRepository.countsOf(listOf(product.id))[product.id]).isEqualTo(1L)

            likeService.unlike(1L, product.id)
            productStatsRefresher.refresh()

            assertThat(productStatsRepository.countsOf(listOf(product.id))[product.id]).isNull()
        }

        @DisplayName("같은 사람이 여러 번 눌러도 1 로 센다.")
        @Test
        fun countsOncePerUser() {
            holdingLock()
            val product = savedProduct()
            repeat(3) { likeService.like(1L, product.id) }

            productStatsRefresher.refresh()

            assertThat(productStatsRepository.countsOf(listOf(product.id))[product.id]).isEqualTo(1L)
        }

        @DisplayName("다시 돌려도 결과가 같다.")
        @Test
        fun isIdempotent() {
            holdingLock()
            val product = savedProduct()
            listOf(1L, 2L).forEach { likeService.like(it, product.id) }

            productStatsRefresher.refresh()
            productStatsRefresher.refresh()

            assertThat(productStatsRepository.countsOf(listOf(product.id))[product.id]).isEqualTo(2L)
        }
    }

    @DisplayName("잠금을 다룰 때,")
    @Nested
    inner class Lock {
        @DisplayName("잠금을 못 잡으면 집계하지 않는다.")
        @Test
        fun skipsWhenLockTaken() {
            every { productStatsLock.tryAcquire() } returns false
            val product = savedProduct()
            likeService.like(1L, product.id)

            val rows = productStatsRefresher.refresh()

            assertAll(
                { assertThat(rows).isEqualTo(0) },
                { assertThat(productStatsRepository.countsOf(listOf(product.id))).isEmpty() },
                { verify(exactly = 0) { productStatsLock.release() } },
            )
        }

        @DisplayName("집계가 끝나면 잠금을 푼다.")
        @Test
        fun releasesLock() {
            holdingLock()

            productStatsRefresher.refresh()

            verify(exactly = 1) { productStatsLock.release() }
        }
    }
}
