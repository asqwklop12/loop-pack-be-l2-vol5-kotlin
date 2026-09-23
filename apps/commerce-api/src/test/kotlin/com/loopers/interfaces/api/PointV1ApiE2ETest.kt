package com.loopers.interfaces.api

import com.loopers.interfaces.api.point.PointV1Dto
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
class PointV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun headers(userId: Long? = this.userId) = HttpHeaders().apply {
        userId?.let { set("X-USER-ID", it.toString()) }
    }

    private val balanceType = object : ParameterizedTypeReference<ApiResponse<PointV1Dto.BalanceResponse>>() {}

    private fun charge(amount: Long, userId: Long? = this.userId) = testRestTemplate.exchange(
        "/api/v1/points/charge",
        HttpMethod.POST,
        HttpEntity(PointV1Dto.ChargeRequest(amount), headers(userId)),
        balanceType,
    )

    @DisplayName("POST /api/v1/points/charge")
    @Nested
    inner class Charge {
        @DisplayName("충전하면, 충전 후 잔액을 반환한다.")
        @Test
        fun returnsBalance_whenCharged() {
            val response = charge(10_000)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.balance).isEqualTo(10_000L)
        }

        @DisplayName("두 번 충전하면, 기존 잔액에 더해진다.")
        @Test
        fun addsToBalance_whenChargedTwice() {
            charge(10_000)

            val response = charge(5_000)

            assertThat(response.body?.data?.balance).isEqualTo(15_000L)
        }

        @DisplayName("충전액이 0 이하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenAmountIsNotPositive() {
            val response = charge(0)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("X-USER-ID 헤더가 없으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUserHeaderIsMissing() {
            val response = charge(10_000, userId = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/points")
    @Nested
    inner class GetBalance {
        @DisplayName("충전한 적이 없으면, 0원을 반환한다.")
        @Test
        fun returnsZero_whenNeverCharged() {
            val response =
                testRestTemplate.exchange("/api/v1/points", HttpMethod.GET, HttpEntity<Any>(headers()), balanceType)

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.balance).isEqualTo(0L)
        }

        @DisplayName("충전한 적이 있으면, 저장된 잔액을 반환한다.")
        @Test
        fun returnsStoredBalance_whenCharged() {
            charge(7_000)

            val response =
                testRestTemplate.exchange("/api/v1/points", HttpMethod.GET, HttpEntity<Any>(headers()), balanceType)

            assertThat(response.body?.data?.balance).isEqualTo(7_000L)
        }
    }
}
