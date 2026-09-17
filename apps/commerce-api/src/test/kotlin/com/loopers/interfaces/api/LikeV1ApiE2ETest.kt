package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val likeJpaRepository: LikeJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun headers(userId: Long = this.userId) =
        HttpHeaders().apply { set("X-USER-ID", userId.toString()) }

    private fun savedProduct(): Product {
        val brand = brandJpaRepository.save(Brand(name = "나이키코리아"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(5)),
        )
    }

    private val voidType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
    private fun endpoint(productId: Long) = "/api/v1/products/$productId/likes"

    private fun like(productId: Long, userId: Long = this.userId) =
        testRestTemplate.exchange(endpoint(productId), HttpMethod.POST, HttpEntity<Any>(headers(userId)), voidType)

    private fun unlike(productId: Long, userId: Long = this.userId) =
        testRestTemplate.exchange(endpoint(productId), HttpMethod.DELETE, HttpEntity<Any>(headers(userId)), voidType)

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class Like {
        @DisplayName("처음 누르면, 관계가 저장된다.")
        @Test
        fun savesRelation_whenFirstTime() {
            val product = savedProduct()

            val response = like(product.id)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(1L)
        }

        @DisplayName("두 번 눌러도 성공하고, 관계는 하나만 남는다.")
        @Test
        fun keepsSingleRelation_whenPressedTwice() {
            val product = savedProduct()
            like(product.id)

            val response = like(product.id)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(1L)
        }

        @DisplayName("서로 다른 사용자가 누르면, 각각 저장된다.")
        @Test
        fun savesEachUser() {
            val product = savedProduct()
            like(product.id, userId = 1L)
            like(product.id, userId = 2L)

            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(2L)
        }

        @DisplayName("없는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            assertThat(like(-1L).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val product = savedProduct()
            productJpaRepository.save(product.apply { delete() })

            assertThat(like(product.id).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class Unlike {
        @DisplayName("누른 적이 있으면, 관계가 지워진다.")
        @Test
        fun removesRelation_whenLiked() {
            val product = savedProduct()
            like(product.id)

            val response = unlike(product.id)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(0L)
        }

        @DisplayName("누른 적이 없어도, 성공으로 끝난다.")
        @Test
        fun succeeds_whenNotLiked() {
            val product = savedProduct()

            assertThat(unlike(product.id).statusCode.is2xxSuccessful).isTrue()
        }

        @DisplayName("상품이 삭제됐어도, 남아 있는 자신의 관계는 취소할 수 있다.")
        @Test
        fun removesRelation_whenProductIsDeleted() {
            val product = savedProduct()
            like(product.id)
            productJpaRepository.save(product.apply { delete() })

            val response = unlike(product.id)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(0L)
        }

        @DisplayName("다른 사용자의 관계는 지우지 않는다.")
        @Test
        fun doesNotRemoveOthersRelation() {
            val product = savedProduct()
            like(product.id, userId = 1L)
            like(product.id, userId = 2L)

            unlike(product.id, userId = 1L)

            assertThat(likeJpaRepository.findByUserIdAndProductId(2L, product.id)).isNotNull()
        }
    }
}
