package com.loopers.pg

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigInteger
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PgCallbackSenderTest {
    private lateinit var server: HttpServer
    private lateinit var received: MutableList<Map<*, *>>
    private lateinit var arrived: CountDownLatch
    private var responseStatus = 200

    private val store = PgTransactionStore()
    private val objectMapper = ObjectMapper()
    private val amount: BigInteger = BigInteger.valueOf(50_000)

    @BeforeEach
    fun setUp() {
        received = Collections.synchronizedList(mutableListOf())
        arrived = CountDownLatch(1)
        responseStatus = 200

        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/callback") { exchange ->
            received.add(objectMapper.readValue(exchange.requestBody, Map::class.java))
            exchange.sendResponseHeaders(responseStatus, -1)
            exchange.close()
            arrived.countDown()
        }
        server.start()
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    private fun sender(
        declineRate: Double = 0.0,
        callbackDropRate: Double = 0.0,
    ) = PgCallbackSender(
        properties = PgSimulatorProperties(
            callbackUrl = "http://localhost:${server.address.port}/callback",
            callbackDelay = Duration.ofMillis(50),
            declineRate = declineRate,
            callbackDropRate = callbackDropRate,
        ),
        store = store,
    )

    private fun acceptedKey(orderId: Long) =
        store.accept(orderId, amount, Instant.now()).transaction.transactionKey

    @DisplayName("지연이 지나면 거래를 확정하고 콜백을 보낸다.")
    @Test
    fun sendsCallbackAfterDelay() {
        val key = acceptedKey(1L)

        sender().scheduleSettlement(key)

        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue()
        assertAll(
            { assertThat(received).hasSize(1) },
            { assertThat(received.first()["transactionKey"]).isEqualTo(key) },
            { assertThat(received.first()["orderId"]).isEqualTo(1) },
            { assertThat(received.first()["status"]).isEqualTo("SUCCESS") },
            { assertThat(received.first()["amount"]).isEqualTo("50000") },
            { assertThat(store.find(key)?.status).isEqualTo(PgStatus.SUCCESS) },
        )
    }

    @DisplayName("거절률이 1이면 FAILED 로 확정되고 콜백도 FAILED 로 간다.")
    @Test
    fun sendsDeclinedCallback() {
        val key = acceptedKey(2L)

        sender(declineRate = 1.0).scheduleSettlement(key)

        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue()
        assertAll(
            { assertThat(received.first()["status"]).isEqualTo("FAILED") },
            { assertThat(store.find(key)?.status).isEqualTo(PgStatus.FAILED) },
        )
    }

    @DisplayName("콜백 드롭률이 1이면 콜백은 오지 않지만 거래는 확정된다.")
    @Test
    fun dropsCallbackButStillSettles() {
        val key = acceptedKey(3L)

        sender(callbackDropRate = 1.0).scheduleSettlement(key)

        assertAll(
            { assertThat(arrived.await(1, TimeUnit.SECONDS)).isFalse() },
            { assertThat(received).isEmpty() },
            { assertThat(store.find(key)?.status).isEqualTo(PgStatus.SUCCESS) },
            { assertThat(store.find(key)?.settledAt).isNotNull() },
        )
    }

    @DisplayName("콜백 수신 측이 실패로 응답해도 다시 보내지 않는다.")
    @Test
    fun doesNotRetry() {
        responseStatus = 500
        val key = acceptedKey(4L)

        sender().scheduleSettlement(key)

        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue()
        Thread.sleep(500)
        assertAll(
            { assertThat(received).hasSize(1) },
            { assertThat(store.find(key)?.status).isEqualTo(PgStatus.SUCCESS) },
        )
    }

    @DisplayName("없는 거래를 확정하려 하면 콜백을 보내지 않는다.")
    @Test
    fun ignoresUnknownTransaction() {
        sender().scheduleSettlement("pgtx_unknown")

        assertAll(
            { assertThat(arrived.await(1, TimeUnit.SECONDS)).isFalse() },
            { assertThat(received).isEmpty() },
        )
    }
}
