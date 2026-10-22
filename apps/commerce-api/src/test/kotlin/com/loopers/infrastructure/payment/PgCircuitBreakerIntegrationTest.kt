package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.shared.Money
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

@SpringBootTest(
    properties = [
        "resilience4j.circuitbreaker.instances.pg.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.pg.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.pg.wait-duration-in-open-state=60s",
        "resilience4j.circuitbreaker.instances.pg.automatic-transition-from-open-to-half-open-enabled=false",
        "pg.read-timeout=200ms",
    ],
)
class PgCircuitBreakerIntegrationTest @Autowired constructor(
    private val paymentGateway: PaymentGateway,
    private val registry: CircuitBreakerRegistry,
) {
    private val card = CardInfo(type = "SAMSUNG", number = "1234-5678-9814-1451")
    private val breaker: CircuitBreaker get() = registry.circuitBreaker(PgClient.PG)

    @BeforeEach
    fun resetBreaker() {
        breaker.reset()
        gatewayCalls = 0
    }

    private fun callFourTimes() = (1..4).map { runCatching { paymentGateway.request(it.toLong(), Money(1_000), card) } }

    @DisplayName("읽기 타임아웃이 반복되면 서킷이 열린다.")
    @Test
    fun opensOnReadTimeout() {
        respondWith = Response(status = 202, body = ACCEPTED, delayMillis = 800)

        callFourTimes()

        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)
    }

    @DisplayName("서킷이 열리면 PG 를 더 부르지 않고 그 자리에서 거절한다.")
    @Test
    fun stopsCallingGatewayWhenOpen() {
        respondWith = Response(status = 202, body = ACCEPTED, delayMillis = 800)
        callFourTimes()
        val callsBefore = gatewayCalls

        val blocked = runCatching { paymentGateway.request(99L, Money(1_000), card) }.exceptionOrNull()

        assertAll(
            { assertThat(blocked).isInstanceOf(CallNotPermittedException::class.java) },
            { assertThat(gatewayCalls).isEqualTo(callsBefore) },
        )
    }

    @DisplayName("PG 가 5xx 를 주면 서킷이 열린다.")
    @Test
    fun opensOnServerError() {
        respondWith = Response(status = 503, body = """{"message":"죽었다"}""")

        callFourTimes()

        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)
    }

    @DisplayName("금액 불일치 409 는 서킷을 열지 않는다. 거절이지만 PG 는 건강하다.")
    @Test
    fun ignoresConflict() {
        respondWith = Response(status = 409, body = """{"message":"이미 다른 금액으로 접수된 주문입니다."}""")

        callFourTimes()

        assertAll(
            { assertThat(breaker.state).isEqualTo(CircuitBreaker.State.CLOSED) },
            { assertThat(gatewayCalls).isEqualTo(4) },
        )
    }

    @DisplayName("카드 형식 400 도 서킷을 열지 않는다.")
    @Test
    fun ignoresBadRequest() {
        respondWith = Response(status = 400, body = """{"message":"카드번호 형식이 올바르지 않습니다."}""")

        callFourTimes()

        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @DisplayName("접수가 막혀도 조회는 서킷을 타지 않는다.")
    @Test
    fun keepsLookupOpen() {
        respondWith = Response(status = 202, body = ACCEPTED, delayMillis = 800)
        callFourTimes()

        respondWith = Response(status = 200, body = SETTLED)
        val found = paymentGateway.findByOrderId(1L)

        assertAll(
            { assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN) },
            { assertThat(found?.orderId).isEqualTo(1L) },
        )
    }

    private data class Response(val status: Int, val body: String, val delayMillis: Long = 0)

    companion object {
        private const val ACCEPTED =
            """{"orderId":1,"amount":1000,"maskedCardNo":"****-****-****-1451","status":"PENDING"}"""
        private const val SETTLED =
            """{"orderId":1,"amount":1000,"maskedCardNo":"****-****-****-1451","status":"SUCCESS"}"""

        private lateinit var server: HttpServer

        @Volatile
        private var gatewayCalls = 0

        @Volatile
        private var respondWith = Response(status = 202, body = ACCEPTED)

        @JvmStatic
        @BeforeAll
        fun startStubGateway() {
            server = HttpServer.create(InetSocketAddress(0), 0)
            // 기본 실행자는 요청을 직렬로 처리한다. 앞선 느린 응답 뒤에 다음 요청이 줄을 선다.
            server.executor = Executors.newFixedThreadPool(8)
            server.createContext("/api/v1/payments") { exchange: HttpExchange ->
                if (exchange.requestMethod == "POST") gatewayCalls++
                val answer = respondWith
                Thread.sleep(answer.delayMillis)
                val bytes = answer.body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(answer.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        @JvmStatic
        @AfterAll
        fun stopStubGateway() = server.stop(0)

        @JvmStatic
        @DynamicPropertySource
        fun gatewayUrl(registry: DynamicPropertyRegistry) {
            registry.add("pg.base-url") { "http://localhost:${server.address.port}" }
        }
    }
}
