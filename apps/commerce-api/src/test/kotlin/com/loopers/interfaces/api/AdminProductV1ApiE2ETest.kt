package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.admin.AdminV1Dto
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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductV1ApiE2ETest @Autowired constructor(
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

    private val productType = object : ParameterizedTypeReference<ApiResponse<AdminV1Dto.ProductV1.Response>>() {}
    private val listType =
        object : ParameterizedTypeReference<ApiResponse<List<AdminV1Dto.ProductV1.Response>>>() {}

    private fun savedBrand(name: String = "나이키코리아") = brandJpaRepository.save(Brand(name = name))

    private fun create(
        brandId: Long,
        name: String = "에어포스1",
        price: Long = 129_000,
        role: String? = "ADMIN",
    ) = testRestTemplate.exchange(
        "/api-admin/v1/products",
        HttpMethod.POST,
        HttpEntity(AdminV1Dto.ProductV1.CreateRequest(brandId, name, price), headers(role)),
        productType,
    )

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    inner class Create {
        @DisplayName("등록하면, 브랜드명과 함께 반환하고 재고는 0으로 시작한다.")
        @Test
        fun createsProduct_withZeroStock() {
            val brand = savedBrand()

            val response = create(brand.id)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.name).isEqualTo("에어포스1") },
                { assertThat(response.body?.data?.brandName).isEqualTo("나이키코리아") },
                { assertThat(response.body?.data?.stock).isEqualTo(0) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("이름이 공백이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            val brand = savedBrand()

            assertThat(create(brand.id, name = "   ").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이름이 2자 미만이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNameIsTooShort() {
            val brand = savedBrand()

            assertThat(create(brand.id, name = "가").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이름이 20자를 넘으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNameIsTooLong() {
            val brand = savedBrand()

            assertThat(create(brand.id, name = "가".repeat(21)).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("없는 브랜드를 참조하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            assertThat(create(brandId = -1L).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val brand = savedBrand()

            assertThat(create(brand.id, role = "USER").statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    inner class Get {
        @DisplayName("좋아요 수와 재고를 함께 반환한다.")
        @Test
        fun returnsLikeCountAndStock() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id
            likeJpaRepository.save(Like(userId = 1L, productId = productId))

            val response = testRestTemplate.exchange(
                "/api-admin/v1/products/$productId",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                productType,
            )

            assertAll(
                { assertThat(response.body?.data?.likeCount).isEqualTo(1L) },
                { assertThat(response.body?.data?.stock).isEqualTo(0) },
            )
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/products/-1",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                productType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    inner class GetAll {
        @DisplayName("상품이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoProduct() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/products",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                listType,
            )

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data).isEmpty()
        }

        @DisplayName("삭제된 상품은 목록에서 제외된다.")
        @Test
        fun excludesDeletedProducts() {
            val brand = savedBrand()
            create(brand.id, name = "에어포스1")
            val secondId = create(brand.id, name = "에어맥스").body!!.data!!.id
            productJpaRepository.save(productJpaRepository.findById(secondId).get().apply { delete() })

            val response = testRestTemplate.exchange(
                "/api-admin/v1/products",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                listType,
            )

            assertThat(response.body?.data?.map { it.name }).containsExactly("에어포스1")
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}/stock")
    @Nested
    inner class ChangeStock {
        private fun changeStock(productId: Long, amount: Int, role: String? = "ADMIN") = testRestTemplate.exchange(
            "/api-admin/v1/products/$productId/stock",
            HttpMethod.PUT,
            HttpEntity(AdminV1Dto.ProductV1.ChangeStockRequest(amount), headers(role)),
            productType,
        )

        @DisplayName("증가 값을 주면, 기존 재고에 더해진다.")
        @Test
        fun changesStock() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id

            changeStock(productId, 5)
            val response = changeStock(productId, 3)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.stock).isEqualTo(8)
            assertThat(productJpaRepository.findById(productId).get().stock.quantity).isEqualTo(8)
        }

        @DisplayName("0을 주면, 재고가 바뀌지 않는다.")
        @Test
        fun keepsStock_whenAmountIsZero() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id
            changeStock(productId, 5)

            val response = changeStock(productId, 0)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.stock).isEqualTo(5)
        }

        @DisplayName("음수를 주면, 400 BAD_REQUEST 응답을 받고 기존 재고가 유지된다.")
        @Test
        fun returnsBadRequest_whenAmountIsNegative() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id
            changeStock(productId, 5)

            val response = changeStock(productId, -1)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(productJpaRepository.findById(productId).get().stock.quantity).isEqualTo(5)
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            assertThat(changeStock(-1L, 5).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id

            assertThat(changeStock(productId, 5, role = "USER").statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    inner class Update {
        private fun update(productId: Long, name: String, price: Long, role: String? = "ADMIN") =
            testRestTemplate.exchange(
                "/api-admin/v1/products/$productId",
                HttpMethod.PUT,
                HttpEntity(AdminV1Dto.ProductV1.UpdateRequest(name, price), headers(role)),
                productType,
            )

        @DisplayName("수정하면 이름과 가격이 바뀌고, 브랜드는 유지된다.")
        @Test
        fun updatesNameAndPrice_andKeepsBrand() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id

            val response = update(productId, "에어포스2", 139_000)

            assertAll(
                { assertThat(response.body?.data?.name).isEqualTo("에어포스2") },
                { assertThat(response.body?.data?.price).isEqualTo(139_000L) },
                { assertThat(response.body?.data?.brandId).isEqualTo(brand.id) },
            )
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            assertThat(update(-1L, "에어포스2", 139_000).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이름이 2자 미만이면, 400 BAD_REQUEST 응답을 받고 기존 값이 유지된다.")
        @Test
        fun returnsBadRequest_whenNameIsTooShort() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id

            val response = update(productId, "가", 139_000)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(productJpaRepository.findById(productId).get().name).isEqualTo("에어포스1")
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val brand = savedBrand()
            val productId = create(brand.id).body!!.data!!.id

            assertThat(update(productId, "에어포스2", 139_000, role = "USER").statusCode)
                .isEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
