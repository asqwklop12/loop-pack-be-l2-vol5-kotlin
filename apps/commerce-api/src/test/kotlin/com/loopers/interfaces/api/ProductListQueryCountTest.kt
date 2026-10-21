package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductStats
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.ProductStatsJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod

/**
 * 목록 조회 한 번에 SQL 이 몇 개 나가는지 재는 기준선이다.
 * 캐시를 붙인 뒤 이 수가 줄었는지 비교하려고 남긴다.
 *
 * 쿼리 수는 데이터 양과 무관하므로 소량으로 재도 같은 값이 나온다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.jpa.properties.hibernate.generate_statistics=true"],
)
class ProductListQueryCountTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val entityManagerFactory: EntityManagerFactory,
    private val likeJpaRepository: LikeJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatsJpaRepository: ProductStatsJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    // 인기순 상위 구간은 캐시를 타므로 Redis 도 비워야 앞 테스트의 id 가 넘어오지 않는다.
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private val statistics: Statistics
        get() = entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    /** 요청 하나가 실행한 SQL 수를 센다. */
    private fun queryCountOf(query: String): Long {
        statistics.clear()

        val response = testRestTemplate.exchange(
            "/api/v1/products$query",
            HttpMethod.GET,
            HttpEntity<Any>(Unit),
            String::class.java,
        )
        assertThat(response.statusCode.is2xxSuccessful).isTrue()

        return statistics.queryExecutionCount
    }

    private fun seed() {
        val nike = brandJpaRepository.save(Brand(name = "나이키코리아"))
        val adidas = brandJpaRepository.save(Brand(name = "아디다스"))

        val airmax = productJpaRepository.save(
            Product(brandId = nike.id, name = "에어맥스", price = Money(30000), stock = Stock(5)),
        )
        val ultraboost = productJpaRepository.save(
            Product(brandId = adidas.id, name = "울트라부스트", price = Money(10000), stock = Stock(3)),
        )
        productJpaRepository.save(
            Product(brandId = nike.id, name = "조던", price = Money(20000), stock = Stock(7)),
        )

        likeJpaRepository.save(Like(userId = 1, productId = airmax.id))
        likeJpaRepository.save(Like(userId = 2, productId = ultraboost.id))

        // 인기순은 좋아요를 세지 않고 집계 테이블을 읽는다. 행이 없으면 목록이 비어 쿼리 수도 달라진다.
        productStatsJpaRepository.save(ProductStats(productId = airmax.id, likeCount = 1))
        productStatsJpaRepository.save(ProductStats(productId = ultraboost.id, likeCount = 1))
    }

    @DisplayName("브랜드 필터 + 가격순 목록 조회는 요청당 SQL 4개를 실행한다.")
    @Test
    fun countsQueriesOfBrandFilteredPriceAscList() {
        seed()
        val brandId = brandJpaRepository.findAll().first().id

        val count = queryCountOf("?brandId=$brandId&sort=price_asc&page=0&size=20")

        println(">>>> brandId+price_asc 요청당 SQL = $count")
        assertThat(count).isEqualTo(4L)
    }

    @DisplayName("좋아요순 목록 조회는 요청당 SQL 5개를 실행한다. 집계에서 id 를 먼저 뽑는 한 개가 더 나간다.")
    @Test
    fun countsQueriesOfLikesDescList() {
        seed()

        val count = queryCountOf("?sort=likes_desc&page=0&size=20")

        println(">>>> likes_desc 요청당 SQL = $count")
        assertThat(count).isEqualTo(5L)
    }
}
