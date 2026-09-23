package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.product.ProductV1Dto
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
class MyLikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun headers(userId: Long = this.userId) =
        HttpHeaders().apply { set("X-USER-ID", userId.toString()) }

    private val listType =
        object : ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>>() {}
    private val voidType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun savedProduct(name: String = "에어포스1"): Product {
        val brand = brandJpaRepository.save(Brand(name = "나이키코리아"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = name, price = Money(1_000), stock = Stock(5)),
        )
    }

    private fun like(productId: Long, userId: Long = this.userId) = testRestTemplate.exchange(
        "/api/v1/products/$productId/likes",
        HttpMethod.POST,
        HttpEntity<Any>(headers(userId)),
        voidType,
    )

    private fun myLikes(pathUserId: Long, requesterId: Long = this.userId) = testRestTemplate.exchange(
        "/api/v1/users/$pathUserId/likes",
        HttpMethod.GET,
        HttpEntity<Any>(headers(requesterId)),
        listType,
    )

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    inner class GetMyLikes {
        @DisplayName("좋아요가 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoLike() {
            val response = myLikes(userId)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data).isEmpty()
        }

        @DisplayName("내가 누른 상품만 반환한다.")
        @Test
        fun returnsOnlyMyLikedProducts() {
            val mine = savedProduct("내가누른상품")
            val others = savedProduct("남이누른상품")
            like(mine.id, userId = 1L)
            like(others.id, userId = 2L)

            val response = myLikes(userId)

            assertThat(response.body?.data?.map { it.name }).containsExactly("내가누른상품")
        }

        @DisplayName("삭제된 상품은 목록에서 제외된다.")
        @Test
        fun excludesDeletedProducts() {
            val alive = savedProduct("살아있는상품")
            val deleted = savedProduct("삭제된상품")
            like(alive.id)
            like(deleted.id)
            productJpaRepository.save(deleted.apply { delete() })

            val response = myLikes(userId)

            assertThat(response.body?.data?.map { it.name }).containsExactly("살아있는상품")
        }

        @DisplayName("브랜드 정보와 좋아요 수를 함께 반환한다.")
        @Test
        fun includesBrandAndLikeCount() {
            val product = savedProduct()
            like(product.id, userId = 1L)
            like(product.id, userId = 2L)

            val response = myLikes(userId)

            assertThat(response.body?.data?.first()?.brandName).isEqualTo("나이키코리아")
            assertThat(response.body?.data?.first()?.likeCount).isEqualTo(2L)
        }

        @DisplayName("남의 목록을 조회하면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotOwner() {
            assertThat(myLikes(pathUserId = 999L, requesterId = userId).statusCode)
                .isEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
