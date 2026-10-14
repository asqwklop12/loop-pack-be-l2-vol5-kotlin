package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 인기순 목록의 캐시 동작.
 *
 * hit 을 확인하는 방법은 "원본을 부르지 않았다" 를 직접 보는 것이 아니라
 * **DB 를 바꿔도 순서가 그대로인지**로 본다. 모킹 없이 관찰할 수 있고,
 * 캐시가 지난 순위를 들고 있다는 사실 자체가 검증 대상이기 때문이다.
 */
@SpringBootTest
class ProductSearchCacheIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productSearchCache: ProductSearchCache,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private val likesDesc = ProductSearch(sort = ProductSort.LIKES_DESC)

    private fun savedBrand() = brandJpaRepository.save(Brand(name = "나이키코리아"))

    private fun savedProduct(brandId: Long, name: String) =
        productJpaRepository.save(
            Product(brandId = brandId, name = name, price = Money(10_000), stock = Stock(5)),
        )

    private fun liked(productId: Long, times: Int) {
        repeat(times) { likeJpaRepository.save(Like(userId = it + 1L, productId = productId)) }
    }

    @DisplayName("인기순을 조회할 때, ")
    @Nested
    inner class SearchByLikes {
        @DisplayName("처음에는 캐시가 비어 있고, 조회하면 정렬 결과가 담긴다.")
        @Test
        fun fillsCacheOnMiss() {
            val brand = savedBrand()
            val few = savedProduct(brand.id, "에어맥스")
            val many = savedProduct(brand.id, "조던")
            liked(few.id, 1)
            liked(many.id, 3)

            assertThat(productSearchCache.find(likesDesc)).isNull()

            val result = productService.search(likesDesc)

            assertThat(result.map { it.id }).containsExactly(many.id, few.id)
            assertThat(productSearchCache.find(likesDesc)).containsExactly(many.id, few.id)
        }

        @DisplayName("캐시가 있으면, 좋아요가 뒤집혀도 담아둔 순서로 반환한다.")
        @Test
        fun servesStaleOrderOnHit() {
            val brand = savedBrand()
            val first = savedProduct(brand.id, "에어맥스")
            val second = savedProduct(brand.id, "조던")
            liked(first.id, 3)
            liked(second.id, 1)

            assertThat(productService.search(likesDesc).map { it.id })
                .containsExactly(first.id, second.id)

            // 2등이 1등을 넘어선다. 캐시가 살아 있으면 순서는 그대로여야 한다.
            liked(second.id, 10)

            assertThat(productService.search(likesDesc).map { it.id })
                .containsExactly(first.id, second.id)
        }

        @DisplayName("캐시를 버리면, 바뀐 좋아요 순서를 반영한다.")
        @Test
        fun reflectsNewOrderAfterEvict() {
            val brand = savedBrand()
            val first = savedProduct(brand.id, "에어맥스")
            val second = savedProduct(brand.id, "조던")
            liked(first.id, 3)
            liked(second.id, 1)
            productService.search(likesDesc)

            liked(second.id, 10)
            productSearchCache.evict(likesDesc)

            assertThat(productService.search(likesDesc).map { it.id })
                .containsExactly(second.id, first.id)
        }

        @DisplayName("담아둔 상품이 판매중지되면, 지난 순위를 쓰더라도 목록에서 빠진다.")
        @Test
        fun dropsSuspendedProductEvenOnHit() {
            val brand = savedBrand()
            val kept = savedProduct(brand.id, "에어맥스")
            val suspended = savedProduct(brand.id, "조던")
            liked(suspended.id, 3)
            liked(kept.id, 1)
            productService.search(likesDesc)

            productJpaRepository.save(suspended.also { it.suspend() })

            assertThat(productService.search(likesDesc).map { it.id }).containsExactly(kept.id)
        }

        @DisplayName("담아둔 상품이 삭제되면, 목록에서 빠진다.")
        @Test
        fun dropsDeletedProductEvenOnHit() {
            val brand = savedBrand()
            val kept = savedProduct(brand.id, "에어맥스")
            val deleted = savedProduct(brand.id, "조던")
            liked(deleted.id, 3)
            liked(kept.id, 1)
            productService.search(likesDesc)

            productJpaRepository.save(deleted.also { it.delete() })

            assertThat(productService.search(likesDesc).map { it.id }).containsExactly(kept.id)
        }

        @DisplayName("조건이 다르면, 서로 다른 캐시를 쓴다.")
        @Test
        fun separatesCacheByCondition() {
            val nike = savedBrand()
            val adidas = brandJpaRepository.save(Brand(name = "아디다스"))
            val nikeProduct = savedProduct(nike.id, "에어맥스")
            val adidasProduct = savedProduct(adidas.id, "울트라부스트")
            liked(nikeProduct.id, 1)
            liked(adidasProduct.id, 3)

            val nikeOnly = ProductSearch(brandId = nike.id, sort = ProductSort.LIKES_DESC)
            productService.search(nikeOnly)

            assertThat(productSearchCache.find(nikeOnly)).containsExactly(nikeProduct.id)
            assertThat(productSearchCache.find(likesDesc)).isNull()
        }
    }

    @DisplayName("인기순이 아닌 정렬은, 캐시를 거치지 않는다.")
    @Test
    fun doesNotCacheOtherSorts() {
        val brand = savedBrand()
        savedProduct(brand.id, "에어맥스")

        ProductSort.entries.filter { it != ProductSort.LIKES_DESC }.forEach { sort ->
            val search = ProductSearch(sort = sort)

            productService.search(search)

            assertThat(productSearchCache.find(search)).isNull()
        }
    }
}
