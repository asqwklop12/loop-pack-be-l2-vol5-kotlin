package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.order.OrderV1Dto
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
class AdminOrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun customerHeaders(userId: Long) =
        HttpHeaders().apply { set("X-USER-ID", userId.toString()) }

    private fun adminHeaders(role: String? = "ADMIN") = HttpHeaders().apply {
        set("X-USER-ID", "1")
        role?.let { set("X-USER-ROLE", it) }
    }

    private val orderType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
    private val listType = object : ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>>() {}

    private fun savedProduct(price: Long = 1_000, stock: Int = 5): Product {
        val brand = brandJpaRepository.save(Brand(name = "테스트브랜드"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(price), stock = Stock(stock)),
        )
    }

    private fun createOrder(productId: Long, userId: Long, quantity: Int = 1) = testRestTemplate.exchange(
        "/api/v1/orders",
        HttpMethod.POST,
        HttpEntity(
            OrderV1Dto.CreateRequest(listOf(OrderV1Dto.CreateRequest.Line(productId, quantity))),
            customerHeaders(userId),
        ),
        orderType,
    )

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    inner class GetAll {
        @DisplayName("주문이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoOrder() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                listType,
            )

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data).isEmpty()
        }

        @DisplayName("구매자가 달라도 모든 주문을 반환한다.")
        @Test
        fun returnsAllOrders_acrossUsers() {
            val product = savedProduct(stock = 5)
            createOrder(product.id, userId = 1L)
            createOrder(product.id, userId = 2L)

            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                listType,
            )

            assertThat(response.body?.data).hasSize(2)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders(role = "USER")),
                listType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    inner class Get {
        @DisplayName("소유자를 가리지 않고 주문을 반환한다.")
        @Test
        fun returnsOrder_regardlessOfOwner() {
            val product = savedProduct(price = 1_000, stock = 5)
            val orderId = createOrder(product.id, userId = 999L, quantity = 2).body!!.data!!.id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders/$orderId",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                orderType,
            )

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo("DRAFT") },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(2_000L) },
                { assertThat(response.body?.data?.lines).hasSize(1) },
            )
        }

        @DisplayName("존재하지 않는 주문이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenOrderDoesNotExist() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders/-1",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                orderType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val product = savedProduct()
            val orderId = createOrder(product.id, userId = 1L).body!!.data!!.id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders/$orderId",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders(role = "USER")),
                orderType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
