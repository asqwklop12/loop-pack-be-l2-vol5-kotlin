package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductListV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val likeJpaRepository: LikeJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private val pageType = object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductPageResponse>>() {}

    private fun savedBrand(name: String) = brandJpaRepository.save(Brand(name = name))

    private fun savedProduct(brandId: Long, name: String, price: Long, stock: Int = 5): Product =
        productJpaRepository.save(
            Product(brandId = brandId, name = name, price = Money(price), stock = Stock(stock)),
        )

    private fun get(query: String = "") =
        testRestTemplate.exchange("/api/v1/products$query", HttpMethod.GET, HttpEntity<Any>(Unit), pageType)

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("상품이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoProduct() {
            val response = get()

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.items).isEmpty() },
                { assertThat(response.body?.data?.totalCount).isEqualTo(0L) },
            )
        }

        @DisplayName("브랜드 정보와 좋아요 수를 함께 반환한다.")
        @Test
        fun includesBrandAndLikeCount() {
            val brand = savedBrand("나이키코리아")
            val product = savedProduct(brand.id, "에어포스1", 1_000)
            likeJpaRepository.save(Like(userId = 1L, productId = product.id))

            val response = get()

            assertAll(
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.brandName).isEqualTo("나이키코리아") },
                { assertThat(response.body?.data?.items?.first()?.likeCount).isEqualTo(1L) },
                { assertThat(response.body?.data?.items?.first()?.stock).isEqualTo(5) },
            )
        }

        @DisplayName("삭제된 상품은 목록에서 제외된다.")
        @Test
        fun excludesDeletedProducts() {
            val brand = savedBrand("나이키코리아")
            savedProduct(brand.id, "에어포스1", 1_000)
            val deleted = savedProduct(brand.id, "에어맥스", 2_000)
            productJpaRepository.save(deleted.apply { delete() })

            val response = get()

            assertThat(response.body?.data?.items?.map { it.name }).containsExactly("에어포스1")
        }

        @DisplayName("브랜드로 거르면, 그 브랜드의 상품만 반환한다.")
        @Test
        fun filtersByBrand() {
            val nike = savedBrand("나이키코리아")
            val adidas = savedBrand("아디다스코리아")
            savedProduct(nike.id, "에어포스1", 1_000)
            savedProduct(adidas.id, "슈퍼스타", 2_000)

            val response = get("?brandId=${nike.id}")

            assertThat(response.body?.data?.items?.map { it.name }).containsExactly("에어포스1")
        }

        @DisplayName("걸리는 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenFilterMatchesNothing() {
            val brand = savedBrand("나이키코리아")
            savedProduct(brand.id, "에어포스1", 1_000)

            val response = get("?brandId=-1")

            assertThat(response.body?.data?.items).isEmpty()
        }

        @DisplayName("price_asc 는 가격이 낮은 순으로 반환한다.")
        @Test
        fun sortsByPriceAsc() {
            val brand = savedBrand("나이키코리아")
            savedProduct(brand.id, "비싼상품", 3_000)
            savedProduct(brand.id, "싼상품", 1_000)
            savedProduct(brand.id, "중간상품", 2_000)

            val response = get("?sort=price_asc")

            assertThat(response.body?.data?.items?.map { it.name })
                .containsExactly("싼상품", "중간상품", "비싼상품")
        }

        @DisplayName("가격이 같으면, id 내림차순으로 가른다.")
        @Test
        fun breaksTieByIdDesc() {
            val brand = savedBrand("나이키코리아")
            val first = savedProduct(brand.id, "먼저등록", 1_000)
            val second = savedProduct(brand.id, "나중등록", 1_000)

            val response = get("?sort=price_asc")

            assertThat(response.body?.data?.items?.map { it.id }).containsExactly(second.id, first.id)
        }

        @DisplayName("likes_desc 는 좋아요가 많은 순으로 반환한다.")
        @Test
        fun sortsByLikesDesc() {
            val brand = savedBrand("나이키코리아")
            val few = savedProduct(brand.id, "적은상품", 1_000)
            val many = savedProduct(brand.id, "많은상품", 1_000)
            likeJpaRepository.save(Like(userId = 1L, productId = few.id))
            likeJpaRepository.save(Like(userId = 1L, productId = many.id))
            likeJpaRepository.save(Like(userId = 2L, productId = many.id))

            val response = get("?sort=likes_desc")

            assertThat(response.body?.data?.items?.map { it.name }).containsExactly("많은상품", "적은상품")
        }

        @DisplayName("latest 는 최근에 등록한 순으로 반환한다.")
        @Test
        fun sortsByLatest() {
            val brand = savedBrand("나이키코리아")
            savedProduct(brand.id, "먼저등록", 1_000)
            savedProduct(brand.id, "나중등록", 2_000)

            val response = get("?sort=latest")

            assertThat(response.body?.data?.items?.map { it.name }).containsExactly("나중등록", "먼저등록")
        }

        @DisplayName("페이지로 나눠 반환하고, 전체 개수를 함께 준다.")
        @Test
        fun paginates() {
            val brand = savedBrand("나이키코리아")
            savedProduct(brand.id, "상품1", 1_000)
            savedProduct(brand.id, "상품2", 2_000)
            savedProduct(brand.id, "상품3", 3_000)

            val response = get("?sort=price_asc&page=1&size=2")

            assertAll(
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.name).isEqualTo("상품3") },
                { assertThat(response.body?.data?.page).isEqualTo(1) },
                { assertThat(response.body?.data?.size).isEqualTo(2) },
                { assertThat(response.body?.data?.totalCount).isEqualTo(3L) },
            )
        }

        @DisplayName("정렬 값이 잘못되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenSortIsInvalid() {
            assertThat(get("?sort=asdf").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
