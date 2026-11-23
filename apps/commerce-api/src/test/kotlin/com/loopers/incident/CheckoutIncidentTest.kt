package com.loopers.incident

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.order.OrderFacade
import com.loopers.domain.brand.Brand
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockReservationRepository
import com.loopers.domain.product.StockReservationStatus
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension::class)
@AutoConfigureObservability(tracing = false)
class CheckoutIncidentTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val objectMapper: ObjectMapper,
    private val paymentRepository: PaymentRepository,
    private val stockReservationRepository: StockReservationRepository,
    private val pointService: PointService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @LocalServerPort
    private var serverPort: Int = 0

    @LocalManagementPort
    private var managementPort: Int = 0

    @BeforeEach
    fun setUp() {
        pg.callbackUrl = "http://localhost:$serverPort/api/v1/payments/callback"
    }

    @AfterEach
    fun tearDown() {
        pg.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("정상 주문 10건은 PG 거래 10건으로 한 번씩 결제되고, 콜백을 받아 모두 성공으로 끝난다.")
    @Test
    fun baseline() {
        val product = savedProduct(price = PRICE, stock = INITIAL_STOCK)
        val users = (1L..ORDER_COUNT).map { BASELINE_USER_OFFSET + it }
        users.forEach { pointService.charge(it, Money(INITIAL_POINT)) }

        assertThat(pg.faultsCleared()).isTrue()
        val orderIds = users.map { userId -> placeAndConfirm(userId, product.id) }
        orderIds.forEach { assertThat(pg.settle(it)).isEqualTo(200) }

        val payments = orderIds.flatMap { paymentRepository.findAllByOrderId(it) }
        val reservations = orderIds.flatMap { stockReservationRepository.findAllByOrderId(it) }
        val health = managementGet("/actuator/health")

        assertAll(
            { assertThat(orderIds).doesNotHaveDuplicates().hasSize(ORDER_COUNT.toInt()) },
            { assertThat(payments).hasSize(ORDER_COUNT.toInt()) },
            { assertThat(payments).allSatisfy { assertThat(it.status).isEqualTo(PaymentStatus.SUCCESS) } },
            { assertThat(payments).allSatisfy { assertThat(it.amount).isEqualTo(Money(PRICE)) } },
            { assertThat(orderIds.map { pg.transactionOf(it)?.status }).containsOnly(LocalPgFixture.SUCCESS) },
            { assertThat(orderIds.map { pg.transactionOf(it)?.amount }).containsOnly(PRICE) },
            { assertThat(orderIds.map { pg.createCountOf(it) }).containsOnly(1) },
            { assertThat(pg.createAttempts()).hasSize(ORDER_COUNT.toInt()) },
            { assertThat(pg.duplicateCreates()).isEmpty() },
            { assertThat(reservations).hasSize(ORDER_COUNT.toInt()) },
            { assertThat(reservations).allSatisfy { assertThat(it.status).isEqualTo(StockReservationStatus.COMMITTED) } },
            { assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(INITIAL_STOCK - ORDER_COUNT.toInt())) },
            { assertThat(users.map { pointService.getBalance(it) }).containsOnly(Money(INITIAL_POINT)) },
            { assertThat(health.get("status").asText()).isEqualTo("UP") },
        )
    }

    @DisplayName("PG 응답을 잃은 주문은 경계 로그에 주문과 timeout 원인이 남고, 기준 시간이 지나면 미확인 결제 지표가 1이 된다. health 는 계속 UP 이다.")
    @Test
    fun observesLostResponse(output: CapturedOutput) {
        val product = savedProduct(price = PRICE, stock = INITIAL_STOCK)
        val userId = OBSERVATION_USER_OFFSET + 1
        pointService.charge(userId, Money(INITIAL_POINT))
        val orderId = placeOrder(userId, product.id)
        pg.dropResponseFor(listOf(orderId))

        val confirmed = confirm(userId, orderId)
        val beforeThreshold = stalePending()
        val afterThreshold = awaitStalePending(expected = 1.0)
        val payment = paymentRepository.findAllByOrderId(orderId).single()
        val health = managementGet("/actuator/health")

        assertAll(
            { assertThat(confirmed.first).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(pg.transactionOf(orderId)?.status).isEqualTo(LocalPgFixture.SUCCESS) },
            { assertThat(output.out).contains("PG 결제 요청. orderId=$orderId paymentKey=${payment.paymentKey}") },
            {
                assertThat(output.out).contains(
                    "PG 결제 응답을 받지 못했다. orderId=$orderId paymentKey=${payment.paymentKey} failure=${OrderFacade.PG_TIMEOUT}",
                )
            },
            { assertThat(beforeThreshold).isEqualTo(0.0) },
            { assertThat(afterThreshold).isEqualTo(1.0) },
            { assertThat(health.get("status").asText()).isEqualTo("UP") },
        )
    }

    private fun savedProduct(price: Long, stock: Int): Product {
        val brand = brandJpaRepository.save(Brand(name = "사고실험브랜드"))

        return productJpaRepository.save(
            Product(brandId = brand.id, name = "사고실험상품", price = Money(price), stock = Stock(stock)),
        )
    }

    private fun placeAndConfirm(userId: Long, productId: Long): Long {
        val orderId = placeOrder(userId, productId)
        assertThat(confirm(userId, orderId).first).isEqualTo(HttpStatus.OK)

        return orderId
    }

    private fun placeOrder(userId: Long, productId: Long): Long {
        val created = post(
            "/api/v1/orders",
            userId,
            mapOf("lines" to listOf(mapOf("productId" to productId, "quantity" to 1))),
        )
        assertThat(created.first).isEqualTo(HttpStatus.OK)

        return created.second.get("data").get("id").asLong()
    }

    private fun confirm(userId: Long, orderId: Long): Pair<HttpStatus, JsonNode> =
        post(
            "/api/v1/orders/$orderId/confirm",
            userId,
            mapOf("method" to "CARD", "cardType" to CARD_TYPE, "cardNo" to CARD_NO),
        )

    private fun stalePending(): Double? {
        val body = testRestTemplate.getForEntity("http://localhost:$managementPort/actuator/prometheus", String::class.java).body

        return STALE_PENDING_LINE.find(body.orEmpty())?.groupValues?.get(1)?.toDouble()
    }

    private fun awaitStalePending(expected: Double): Double? {
        val deadline = System.nanoTime() + STALE_PENDING_WAIT.toNanos()
        var value = stalePending()
        while (value != expected && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL.toMillis())
            value = stalePending()
        }

        return value
    }

    private fun post(path: String, userId: Long, body: Any): Pair<HttpStatus, JsonNode> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-USER-ID", userId.toString())
        }
        val response = testRestTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headers), String::class.java)

        return HttpStatus.valueOf(response.statusCode.value()) to objectMapper.readTree(response.body)
    }

    private fun managementGet(path: String): JsonNode {
        val response = testRestTemplate.getForEntity("http://localhost:$managementPort$path", String::class.java)

        return objectMapper.readTree(response.body)
    }

    companion object {
        private const val ORDER_COUNT = 10L
        private const val BASELINE_USER_OFFSET = 1_000L
        private const val PRICE = 39_000L
        private const val INITIAL_STOCK = 30
        private const val INITIAL_POINT = 50_000L
        private const val CARD_TYPE = "SAMSUNG"
        private const val CARD_NO = "1234-5678-9814-1451"
        private const val OBSERVATION_USER_OFFSET = 2_000L

        private val STALE_PENDING_AFTER = Duration.ofSeconds(2)
        private val STALE_PENDING_WAIT = Duration.ofSeconds(5)
        private val POLL_INTERVAL = Duration.ofMillis(200)
        private val STALE_PENDING_LINE = Regex("""(?m)^payment_pending_stale(?:\{[^}]*})? (\S+)$""")

        private val pg = LocalPgFixture(dropHold = Duration.ofMillis(1_500))

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("pg.base-url") { pg.baseUrl }
            registry.add("pg.read-timeout") { "500ms" }
            registry.add("management.server.port") { "0" }
            registry.add("payment.metrics.stale-pending-after") { STALE_PENDING_AFTER.toString() }
        }

        @JvmStatic
        @AfterAll
        fun closePg() = pg.close()
    }
}
