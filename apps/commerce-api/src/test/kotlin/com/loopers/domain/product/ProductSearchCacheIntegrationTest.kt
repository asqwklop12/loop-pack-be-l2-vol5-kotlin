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
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    /** 집계 쿼리가 몇 번 도는지 세려고 감싼다. 동작은 그대로 둔다. */
    @MockitoSpyBean
    private lateinit var productRepository: ProductRepository

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
        clearInvocations(productRepository)
    }

    private val likesDesc = ProductSearch(sort = ProductSort.LIKES_DESC)

    private fun savedBrand() = brandJpaRepository.save(Brand(name = "나이키코리아"))

    private fun savedProduct(brandId: Long, name: String) =
        productJpaRepository.save(
            Product(brandId = brandId, name = name, price = Money(10_000), stock = Stock(5)),
        )

    /**
     * 사용자 번호는 계속 올라간다. 같은 상품에 두 번 부르면 `uk_like_user_product` 에 걸리므로
     * 호출마다 1 부터 다시 매기지 않는다.
     */
    private var userSeq = 0L

    private fun liked(productId: Long, times: Int) {
        repeat(times) { likeJpaRepository.save(Like(userId = ++userSeq, productId = productId)) }
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

            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, null)).isNull()

            val result = productService.search(likesDesc)

            assertThat(result.map { it.id }).containsExactly(many.id, few.id)
            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, null)).containsExactly(many.id, few.id)
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
            productSearchCache.evict(ProductSort.LIKES_DESC, null)

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

            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, nike.id)).containsExactly(nikeProduct.id)
            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, null)).isNull()
        }
    }

    /**
     * 캐시는 상위 [ProductSearchCache.CACHED_RANKS] 위만 담는다.
     * 그 범위를 벗어나는 요청은 담지 않은 구간이므로 캐시로 답할 수 없다.
     */
    @DisplayName("캐시가 담은 순위를 기준으로, ")
    @Nested
    inner class CachedWindow {
        @DisplayName("안쪽 요청은 캐시에서 잘라 쓰고, 담기는 것은 상위 전체다.")
        @Test
        fun slicesFromCachedTop() {
            val brand = savedBrand()
            val products = (1..5).map { savedProduct(brand.id, "상품$it") }
            products.forEachIndexed { i, p -> liked(p.id, 5 - i) }

            val firstTwo = productService.search(ProductSearch(sort = ProductSort.LIKES_DESC, page = 0, size = 2))

            assertThat(firstTwo.map { it.id }).containsExactly(products[0].id, products[1].id)
            // size=2 로 들어왔어도 담기는 것은 상위 전체여야 한다. 5개뿐이라 5개가 담긴다.
            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, null))
                .containsExactly(*products.map { it.id }.toTypedArray())
        }

        @DisplayName("담은 순위를 넘는 뒤 페이지는, 캐시를 거치지 않는다.")
        @Test
        fun skipsCacheBeyondCachedRanks() {
            val brand = savedBrand()
            val product = savedProduct(brand.id, "에어맥스")
            liked(product.id, 1)

            val beyond = ProductSearch(sort = ProductSort.LIKES_DESC, page = 1, size = ProductSearchCache.CACHED_RANKS)
            productService.search(beyond)

            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, null)).isNull()
        }

        @DisplayName("담은 순위보다 큰 size 요청도, 캐시를 거치지 않는다.")
        @Test
        fun skipsCacheForLargerSize() {
            val brand = savedBrand()
            val product = savedProduct(brand.id, "에어맥스")
            liked(product.id, 1)

            val wider = ProductSearch(sort = ProductSort.LIKES_DESC, size = ProductSearchCache.CACHED_RANKS + 1)
            productService.search(wider)

            assertThat(productSearchCache.findTop(ProductSort.LIKES_DESC, null)).isNull()
        }
    }

    /**
     * TTL 이 끝나는 순간 들어온 요청이 전부 같은 집계 쿼리를 도는 것을 막는다.
     * 측정에서 41개가 동시에 돌아 혼자면 2.6초인 쿼리가 각각 20초가 됐다.
     */
    @DisplayName("캐시가 비어 있을 때 여러 요청이 동시에 들어와도, ")
    @Nested
    inner class RefillLock {
        @DisplayName("비싼 집계 쿼리는 한 번만 돈다.")
        @Test
        fun loadsOriginOnlyOnce() {
            val brand = savedBrand()
            val products = (1..3).map { savedProduct(brand.id, "상품$it") }
            products.forEachIndexed { i, p -> liked(p.id, 3 - i) }

            val results = inParallel(threads = THREADS) { productService.search(likesDesc).map { it.id } }

            // 대기한 쪽도 findAllByIds 는 돌기 때문에 전체 쿼리 수로는 갈리지 않는다.
            // 락이 막는 것은 집계 쿼리뿐이므로 그것만 센다.
            verify(productRepository, times(1)).search(any())
            assertThat(results).allSatisfy { assertThat(it).isEqualTo(products.map { p -> p.id }) }
        }

        @DisplayName("채우던 쪽이 끝내지 못해도, 기다리다 직접 읽어서 답한다.")
        @Test
        fun fallsBackWhenRefillNeverFinishes() {
            val brand = savedBrand()
            val product = savedProduct(brand.id, "에어맥스")
            liked(product.id, 1)

            // 아무도 풀지 않는 잠금을 미리 잡아 둔다.
            assertThat(productSearchCache.tryStartRefill(ProductSort.LIKES_DESC, null)).isTrue()

            assertThat(productService.search(likesDesc).map { it.id }).containsExactly(product.id)
        }
    }

    private companion object {
        /** 만료 순간에 몰리는 요청을 흉내 내는 수. */
        const val THREADS = 10
    }

    private fun <T> inParallel(threads: Int, block: () -> T): List<T> {
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)

        return try {
            val futures = (1..threads).map {
                pool.submit<T> {
                    ready.countDown()
                    go.await()
                    block()
                }
            }
            ready.await(10, TimeUnit.SECONDS)
            go.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
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

            assertThat(productSearchCache.findTop(sort, null)).isNull()
        }
    }
}
