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
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val likeJpaRepository: LikeJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private val responseType = object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
    private fun endpoint(productId: Long) = "/api/v1/products/$productId"

    private fun savedProduct(): Product {
        val brand = brandJpaRepository.save(Brand(name = "나이키코리아"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(5)),
        )
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    inner class Get {
        @DisplayName("브랜드 정보와 좋아요 수를 함께 반환한다.")
        @Test
        fun returnsBrandAndLikeCount() {
            val product = savedProduct()
            likeJpaRepository.save(Like(userId = 1L, productId = product.id))
            likeJpaRepository.save(Like(userId = 2L, productId = product.id))

            val response =
                testRestTemplate.exchange(endpoint(product.id), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.name).isEqualTo("에어포스1") },
                { assertThat(response.body?.data?.price).isEqualTo(129_000L) },
                { assertThat(response.body?.data?.stock).isEqualTo(5) },
                { assertThat(response.body?.data?.brandName).isEqualTo("나이키코리아") },
                { assertThat(response.body?.data?.likeCount).isEqualTo(2L) },
            )
        }

        @DisplayName("좋아요가 없으면, 0을 반환한다.")
        @Test
        fun returnsZeroLikeCount_whenNoLikes() {
            val product = savedProduct()

            val response =
                testRestTemplate.exchange(endpoint(product.id), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            assertThat(response.body?.data?.likeCount).isEqualTo(0L)
        }

        @DisplayName("없는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val response =
                testRestTemplate.exchange(endpoint(-1L), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val product = savedProduct()
            productJpaRepository.save(product.apply { delete() })

            val response =
                testRestTemplate.exchange(endpoint(product.id), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
