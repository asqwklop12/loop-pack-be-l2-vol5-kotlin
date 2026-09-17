package com.loopers.interfaces.api

import com.loopers.domain.point.PointService
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
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val pointService: PointService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun headers(userId: Long = this.userId) =
        HttpHeaders().apply { set("X-USER-ID", userId.toString()) }

    private fun savedProduct(price: Long = 1_000, stock: Int = 5): Product {
        val brand = brandJpaRepository.save(Brand(name = "브랜드${System.nanoTime()}"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(price), stock = Stock(stock)),
        )
    }

    private val orderType = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}
    private val listType = object : ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>>() {}

    private fun createOrder(productId: Long, quantity: Int = 1, userId: Long = this.userId) =
        testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(
                OrderV1Dto.CreateRequest(listOf(OrderV1Dto.CreateRequest.Line(productId, quantity))),
                headers(userId),
            ),
            orderType,
        )

    private fun confirm(orderId: Long, userId: Long = this.userId) = testRestTemplate.exchange(
        "/api/v1/orders/$orderId/confirm",
        HttpMethod.POST,
        HttpEntity<Any>(headers(userId)),
        orderType,
    )

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class Create {
        @DisplayName("주문을 만들면, DRAFT 와 합계를 반환하고 재고가 차감된다.")
        @Test
        fun returnsDraftOrder() {
            val product = savedProduct(price = 1_000, stock = 5)

            val response = createOrder(product.id, quantity = 2)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo("DRAFT") },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(2_000L) },
                { assertThat(response.body?.data?.paidAmount).isNull() },
                { assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(3)) },
            )
        }

        @DisplayName("없는 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            assertThat(createOrder(-1L).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val product = savedProduct()
            productJpaRepository.save(product.apply { delete() })

            assertThat(createOrder(product.id).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("수량이 0이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenQuantityIsZero() {
            val product = savedProduct()

            assertThat(createOrder(product.id, quantity = 0).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("같은 상품이 두 번 들어오면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenProductIsDuplicated() {
            val product = savedProduct()

            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateRequest(
                        listOf(
                            OrderV1Dto.CreateRequest.Line(product.id, 1),
                            OrderV1Dto.CreateRequest.Line(product.id, 1),
                        ),
                    ),
                    headers(),
                ),
                orderType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/confirm")
    @Nested
    inner class Confirm {
        @DisplayName("확정하면, CONFIRMED 와 결제액을 반환하고 잔액이 줄어든다. 재고는 생성 때 이미 줄었다.")
        @Test
        fun returnsConfirmedOrder() {
            val product = savedProduct(price = 7_000, stock = 5)
            val orderId = createOrder(product.id).body!!.data!!.id
            pointService.charge(userId, Money(10_000))

            val response = confirm(orderId)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo("CONFIRMED") },
                { assertThat(response.body?.data?.paidAmount).isEqualTo(7_000L) },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(3_000)) },
                { assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(4)) },
            )
        }

        @DisplayName("잔액이 부족하면, 400 BAD_REQUEST 응답을 받고 주문이 DRAFT 로 남는다.")
        @Test
        fun returnsBadRequest_whenBalanceIsNotEnough() {
            val product = savedProduct(price = 10_000, stock = 5)
            val orderId = createOrder(product.id).body!!.data!!.id

            val response = confirm(orderId)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(4))
        }

        @DisplayName("이미 확정된 주문을 다시 확정하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenAlreadyConfirmed() {
            val product = savedProduct(price = 1_000, stock = 5)
            val orderId = createOrder(product.id).body!!.data!!.id
            pointService.charge(userId, Money(10_000))
            confirm(orderId)

            assertThat(confirm(orderId).statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("남의 주문이면, 없는 주문과 같은 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenNotOwner() {
            val product = savedProduct()
            val orderId = createOrder(product.id).body!!.data!!.id

            assertThat(confirm(orderId, userId = 999L).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    inner class GetAll {
        @DisplayName("내 주문만 반환한다.")
        @Test
        fun returnsOnlyMyOrders() {
            val product = savedProduct(stock = 5)
            createOrder(product.id, userId = userId)
            createOrder(product.id, userId = 999L)

            val response =
                testRestTemplate.exchange("/api/v1/orders", HttpMethod.GET, HttpEntity<Any>(headers()), listType)

            assertThat(response.body?.data).hasSize(1)
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    inner class Get {
        @DisplayName("품목·수량·단가를 함께 반환한다.")
        @Test
        fun returnsItems() {
            val product = savedProduct(price = 1_000, stock = 5)
            val orderId = createOrder(product.id, quantity = 2).body!!.data!!.id

            val response = testRestTemplate.exchange(
                "/api/v1/orders/$orderId",
                HttpMethod.GET,
                HttpEntity<Any>(headers()),
                orderType,
            )

            assertAll(
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.quantity).isEqualTo(2) },
                { assertThat(response.body?.data?.items?.first()?.unitPrice).isEqualTo(1_000L) },
                { assertThat(response.body?.data?.items?.first()?.amount).isEqualTo(2_000L) },
            )
        }

        @DisplayName("남의 주문이면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenNotOwner() {
            val product = savedProduct()
            val orderId = createOrder(product.id).body!!.data!!.id

            val response = testRestTemplate.exchange(
                "/api/v1/orders/$orderId",
                HttpMethod.GET,
                HttpEntity<Any>(headers(userId = 999L)),
                orderType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
