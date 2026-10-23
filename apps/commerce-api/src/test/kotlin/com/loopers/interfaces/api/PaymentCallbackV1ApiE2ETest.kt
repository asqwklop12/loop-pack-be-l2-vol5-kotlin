package com.loopers.interfaces.api

import com.loopers.domain.payment.PaymentStatus
import com.loopers.interfaces.api.payment.PaymentV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentCallbackV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val path = "/api/v1/payments/callback"

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun send(body: Any): org.springframework.http.ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

        return testRestTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headers), String::class.java)
    }

    @DisplayName("PG 가 승인을 알려오면 받아들인다.")
    @Test
    fun acceptsApproval() {
        val response = send(
            PaymentV1Dto.CallbackRequest(
                transactionKey = "pgtx_1",
                orderId = 1001L,
                status = PaymentStatus.SUCCESS,
                amount = "50000",
                settledAt = Instant.parse("2026-10-18T12:00:00Z"),
            ),
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body).contains("\"result\":\"SUCCESS\"") },
        )
    }

    @DisplayName("PG 가 거절을 알려오면 사유와 함께 받아들인다.")
    @Test
    fun acceptsDecline() {
        val response = send(
            PaymentV1Dto.CallbackRequest(
                transactionKey = "pgtx_2",
                orderId = 1002L,
                status = PaymentStatus.FAILED,
                amount = "50000",
                declineCode = "LIMIT_EXCEEDED",
                declineReason = "한도초과",
            ),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    /**
     * 시뮬레이터가 `settledAt` 을 epoch 초(소수점 포함)로 보낸다. 조회 응답의 ISO 문자열과
     * 형식이 다르므로, 둘 다 읽히는지 본다. 실제 PG 와 붙일 때 가장 먼저 깨지는 자리다.
     */
    @DisplayName("확정 시각이 epoch 숫자로 와도 읽는다.")
    @Test
    fun readsEpochSettledAt() {
        val response = send(
            mapOf(
                "transactionKey" to "pgtx_3",
                "orderId" to 1003L,
                "status" to "SUCCESS",
                "amount" to "50000",
                "settledAt" to 1792334150.698184,
            ),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @DisplayName("확정 시각이 ISO 문자열로 와도 읽는다.")
    @Test
    fun readsIsoSettledAt() {
        val response = send(
            mapOf(
                "transactionKey" to "pgtx_4",
                "orderId" to 1004L,
                "status" to "SUCCESS",
                "amount" to "50000",
                "settledAt" to "2026-10-18T12:00:00Z",
            ),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @DisplayName("같은 콜백이 두 번 와도 둘 다 받아들인다. 거르는 것은 아직 없다.")
    @Test
    fun acceptsDuplicate() {
        val body = PaymentV1Dto.CallbackRequest(
            transactionKey = "pgtx_5",
            orderId = 1005L,
            status = PaymentStatus.SUCCESS,
            amount = "50000",
        )

        val first = send(body)
        val second = send(body)

        assertAll(
            { assertThat(first.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(second.statusCode).isEqualTo(HttpStatus.OK) },
        )
    }

    @DisplayName("거래키가 없으면 거절한다.")
    @Test
    fun rejectsMissingTransactionKey() {
        val response = send(
            mapOf("orderId" to 1006L, "status" to "SUCCESS", "amount" to "50000"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @DisplayName("모르는 상태값이면 거절한다.")
    @Test
    fun rejectsUnknownStatus() {
        val response = send(
            mapOf(
                "transactionKey" to "pgtx_7",
                "orderId" to 1007L,
                "status" to "WAT",
                "amount" to "50000",
            ),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
