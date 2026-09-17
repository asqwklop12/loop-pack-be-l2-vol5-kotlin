package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
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
class AdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun headers(role: String? = "ADMIN") = HttpHeaders().apply {
        set("X-USER-ID", "1")
        role?.let { set("X-USER-ROLE", it) }
    }

    private val voidType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun savedBrand(name: String = "나이키") = brandJpaRepository.save(Brand(name = name))

    private fun savedProduct(brandId: Long, stock: Int = 0) = productJpaRepository.save(
        Product(brandId = brandId, name = "에어포스1", price = Money(129_000), stock = Stock(stock)),
    )

    private fun delete(url: String, role: String? = "ADMIN") =
        testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity<Any>(headers(role)), voidType)

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    inner class DeleteBrand {
        @DisplayName("연결된 상품이 없으면, 브랜드가 삭제된다.")
        @Test
        fun deletesBrand_whenNoProduct() {
            val brand = savedBrand()

            val response = delete("/api-admin/v1/brands/${brand.id}")

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNotNull()
        }

        @DisplayName("삭제되지 않은 상품이 남아 있으면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenActiveProductRemains() {
            val brand = savedBrand()
            savedProduct(brand.id)

            val response = delete("/api-admin/v1/brands/${brand.id}")

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("없는 브랜드면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            assertThat(delete("/api-admin/v1/brands/-1").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받고 삭제되지 않는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val brand = savedBrand()

            val response = delete("/api-admin/v1/brands/${brand.id}", role = "USER")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
            assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNull()
        }

        @DisplayName("역할 헤더가 없으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRoleHeaderIsMissing() {
            val brand = savedBrand()

            assertThat(delete("/api-admin/v1/brands/${brand.id}", role = null).statusCode)
                .isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    inner class DeleteProduct {
        @DisplayName("재고가 0이면, 상품과 좋아요가 함께 삭제된다.")
        @Test
        fun deletesProductAndLikes_whenStockIsEmpty() {
            val brand = savedBrand()
            val product = savedProduct(brand.id, stock = 0)
            likeJpaRepository.save(Like(userId = 1L, productId = product.id))

            val response = delete("/api-admin/v1/products/${product.id}")

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(productJpaRepository.findById(product.id).get().deletedAt).isNotNull()
            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(0L)
        }

        @DisplayName("재고가 남아 있으면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenStockRemains() {
            val brand = savedBrand()
            val product = savedProduct(brand.id, stock = 3)

            assertThat(delete("/api-admin/v1/products/${product.id}").statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("없는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            assertThat(delete("/api-admin/v1/products/-1").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받고 삭제되지 않는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val brand = savedBrand()
            val product = savedProduct(brand.id, stock = 0)

            val response = delete("/api-admin/v1/products/${product.id}", role = "USER")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
            assertThat(productJpaRepository.findById(product.id).get().deletedAt).isNull()
        }
    }
}
