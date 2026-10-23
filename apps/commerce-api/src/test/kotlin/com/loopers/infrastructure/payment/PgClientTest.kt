package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.shared.Money
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration

class PgClientTest {
    private lateinit var server: HttpServer
    private var requestCount = 0

    @BeforeEach
    fun setUp() {
        requestCount = 0
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    private fun respond(path: String, status: Int, body: String, delay: Duration = Duration.ZERO) {
        server.createContext(path) { exchange: HttpExchange ->
            requestCount++
            Thread.sleep(delay.toMillis())
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private val card = CardInfo(type = "SAMSUNG", number = "1234-5678-9814-1451")

    private fun client(
        readTimeout: Duration = Duration.ofSeconds(3),
        baseUrl: String = "http://localhost:${server.address.port}",
    ) = PgClient(
        PgProperties(
            baseUrl = baseUrl,
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = readTimeout,
        ),
    )

    @DisplayName("결제를 접수할 때,")
    @Nested
    inner class Request {
        @DisplayName("PG 가 접수하면 미확정 거래를 받는다.")
        @Test
        fun returnsPendingTransaction() {
            respond(
                "/api/v1/payments",
                202,
                """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}""",
            )

            val transaction = client().request(orderId = 1L, amount = Money(50_000), card = card)

            assertAll(
                { assertThat(transaction.orderId).isEqualTo(1L) },
                { assertThat(transaction.amount).isEqualTo(Money(50_000)) },
                { assertThat(transaction.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(transaction.isFinal).isFalse() },
                { assertThat(transaction.maskedCardNo).isEqualTo("****-****-****-1451") },
            )
        }

        @DisplayName("PG 가 읽기 제한 시간보다 늦게 답하면 실패한다.")
        @Test
        fun failsOnReadTimeout() {
            respond(
                "/api/v1/payments",
                202,
                """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}""",
                delay = Duration.ofMillis(800),
            )

            assertThatThrownBy { client(readTimeout = Duration.ofMillis(200)).request(1L, Money(50_000), card) }
                .isInstanceOf(RestClientException::class.java)
                .hasRootCauseInstanceOf(SocketTimeoutException::class.java)
        }

        @DisplayName("PG 가 떠 있지 않으면 실패한다.")
        @Test
        fun failsWhenGatewayIsDown() {
            assertThatThrownBy { client(baseUrl = "http://localhost:1").request(1L, Money(50_000), card) }
                .isInstanceOf(ResourceAccessException::class.java)
                .hasRootCauseInstanceOf(ConnectException::class.java)
        }

        @DisplayName("읽기 타임아웃과 연결 거부는 서로 다른 예외로 온다.")
        @Test
        fun separatesTimeoutFromRefusal() {
            respond(
                "/api/v1/payments",
                202,
                """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}""",
                delay = Duration.ofMillis(800),
            )

            val timedOut = runCatching { client(readTimeout = Duration.ofMillis(200)).request(1L, Money(50_000), card) }
                .exceptionOrNull()
            val refused = runCatching { client(baseUrl = "http://localhost:1").request(1L, Money(50_000), card) }
                .exceptionOrNull()

            assertAll(
                { assertThat(timedOut).isNotInstanceOf(ResourceAccessException::class.java) },
                { assertThat(refused).isInstanceOf(ResourceAccessException::class.java) },
            )
        }

        @DisplayName("제한 시간 안에 실패해도 PG 에는 요청이 도달해 있을 수 있다.")
        @Test
        fun leavesRequestBehindOnTimeout() {
            respond(
                "/api/v1/payments",
                202,
                """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}""",
                delay = Duration.ofMillis(800),
            )

            runCatching { client(readTimeout = Duration.ofMillis(200)).request(1L, Money(50_000), card) }

            assertThat(requestCount).isEqualTo(1)
        }
    }

    @DisplayName("카드 정보를 넘길 때,")
    @Nested
    inner class SendCard {
        @DisplayName("카드사와 카드번호를 PG 에 그대로 넘긴다.")
        @Test
        fun forwardsCard() {
            var sent = ""
            server.createContext("/api/v1/payments") { exchange ->
                sent = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val bytes = """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}"""
                    .toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(202, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            client().request(1L, Money(50_000), card)

            assertAll(
                { assertThat(sent).contains(""""cardType":"SAMSUNG"""") },
                { assertThat(sent).contains(""""cardNo":"1234-5678-9814-1451"""") },
            )
        }

        @DisplayName("응답에서 받는 카드번호는 가려진 값이다.")
        @Test
        fun keepsOnlyMaskedCardNo() {
            respond(
                "/api/v1/payments",
                202,
                """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}""",
            )

            val transaction = client().request(1L, Money(50_000), card)

            assertAll(
                { assertThat(transaction.maskedCardNo).doesNotContain("1234-5678") },
                { assertThat(transaction.toString()).doesNotContain("1234-5678") },
            )
        }
    }

    @DisplayName("거래를 조회할 때,")
    @Nested
    inner class Find {
        @DisplayName("주문으로 조회한다.")
        @Test
        fun findsByOrderId() {
            respond(
                "/api/v1/payments",
                200,
                """{"orderId":1,"amount":50000,"maskedCardNo":"****-****-****-1451","status":"SUCCESS"}""",
            )

            assertThat(client().findByOrderId(1L)?.orderId).isEqualTo(1L)
        }
    }
}
