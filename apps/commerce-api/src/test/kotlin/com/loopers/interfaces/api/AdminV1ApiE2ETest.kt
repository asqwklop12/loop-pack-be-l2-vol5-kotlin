package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
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

    private fun savedBrand(name: String = "나이키코리아") = brandJpaRepository.save(Brand(name = name))

    private fun savedProduct(brandId: Long, stock: Int = 0) = productJpaRepository.save(
        Product(brandId = brandId, name = "에어포스1", price = Money(129_000), stock = Stock(stock)),
    )

    private fun delete(url: String, role: String? = "ADMIN") =
        testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity<Any>(headers(role)), voidType)

    private val brandType = object : ParameterizedTypeReference<ApiResponse<AdminV1Dto.BrandV1.Response>>() {}

    private fun createBrand(name: String = "나이키코리아", role: String? = "ADMIN") = testRestTemplate.exchange(
        "/api-admin/v1/brands",
        HttpMethod.POST,
        HttpEntity(AdminV1Dto.BrandV1.CreateRequest(name), headers(role)),
        brandType,
    )

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    inner class CreateBrand {
        @DisplayName("유효한 이름이면, 저장된 브랜드를 반환한다.")
        @Test
        fun createsBrand_whenNameIsValid() {
            val response = createBrand()

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.name).isEqualTo("나이키코리아")
        }

        @DisplayName("이름이 공백이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            assertThat(createBrand(name = "     ").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이름이 4자 미만이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNameIsTooShort() {
            assertThat(createBrand(name = "나이키").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이름이 8자를 넘으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNameIsTooLong() {
            assertThat(createBrand(name = "가".repeat(9)).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            assertThat(createBrand(role = "USER").statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    private val brandListType =
        object : ParameterizedTypeReference<ApiResponse<List<AdminV1Dto.BrandV1.Response>>>() {}

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드면, 상세를 반환한다.")
        @Test
        fun returnsBrand_whenExists() {
            val brand = savedBrand()

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                brandType,
            )

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.name).isEqualTo("나이키코리아")
        }

        @DisplayName("존재하지 않는 브랜드면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/-1",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                brandType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = savedBrand()
            brandJpaRepository.save(brand.apply { delete() })

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                brandType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    inner class GetBrands {
        @DisplayName("브랜드가 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoBrand() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                brandListType,
            )

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data).isEmpty()
        }

        @DisplayName("삭제된 브랜드는 목록에서 제외된다.")
        @Test
        fun excludesDeletedBrands() {
            savedBrand(name = "나이키코리아")
            val adidas = savedBrand(name = "아디다스")
            brandJpaRepository.save(adidas.apply { delete() })

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                brandListType,
            )

            assertThat(response.body?.data?.map { it.name }).containsExactly("나이키코리아")
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    inner class UpdateBrand {
        private fun update(brandId: Long, name: String, role: String? = "ADMIN") = testRestTemplate.exchange(
            "/api-admin/v1/brands/$brandId",
            HttpMethod.PUT,
            HttpEntity(AdminV1Dto.BrandV1.UpdateRequest(name), headers(role)),
            brandType,
        )

        @DisplayName("유효한 이름이면, 이름이 바뀐다.")
        @Test
        fun updatesName_whenValid() {
            val brand = savedBrand()

            val response = update(brand.id, "아디다스")

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.name).isEqualTo("아디다스")
            assertThat(brandJpaRepository.findById(brand.id).get().name).isEqualTo("아디다스")
        }

        @DisplayName("존재하지 않는 브랜드면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            assertThat(update(-1L, "아디다스").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이름이 4자 미만이면, 400 BAD_REQUEST 응답을 받고 기존 이름이 유지된다.")
        @Test
        fun returnsBadRequest_whenNameIsTooShort() {
            val brand = savedBrand()

            val response = update(brand.id, "나이키")

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(brandJpaRepository.findById(brand.id).get().name).isEqualTo("나이키코리아")
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val brand = savedBrand()

            assertThat(update(brand.id, "아디다스", role = "USER").statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

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
