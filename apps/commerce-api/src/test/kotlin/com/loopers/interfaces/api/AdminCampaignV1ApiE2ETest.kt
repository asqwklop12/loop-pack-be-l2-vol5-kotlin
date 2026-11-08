package com.loopers.interfaces.api

import com.loopers.infrastructure.campaign.CampaignJpaRepository
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
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCampaignV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val campaignJpaRepository: CampaignJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private val now = ZonedDateTime.now()

    private val campaignType =
        object : ParameterizedTypeReference<ApiResponse<AdminV1Dto.CampaignV1.Response>>() {}

    private fun createCampaign(
        totalLimit: Int = 100,
        startDate: ZonedDateTime = now.plusDays(1),
        endDate: ZonedDateTime = now.plusDays(8),
        role: String = "ADMIN",
    ) = testRestTemplate.exchange(
        "/api-admin/v1/campaigns",
        HttpMethod.POST,
        HttpEntity(
            AdminV1Dto.CampaignV1.CreateRequest("신규가입 쿠폰", totalLimit, startDate, endDate),
            HttpHeaders().apply { set("X-USER-ROLE", role) },
        ),
        campaignType,
    )

    @DisplayName("POST /api-admin/v1/campaigns")
    @Nested
    inner class CreateCampaign {
        @DisplayName("유효한 값이면, 발급 가능 상태로 발급 수 0인 캠페인이 저장된다.")
        @Test
        fun createsCampaign_whenValid() {
            val response = createCampaign()

            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.issuable).isTrue() },
                { assertThat(data?.issuedCount).isZero() },
                { assertThat(data?.totalLimit).isEqualTo(100) },
                { assertThat(campaignJpaRepository.findById(data!!.id)).isPresent() },
            )
        }

        @DisplayName("총 발급 한도가 0이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenTotalLimitIsZero() {
            assertThat(createCampaign(totalLimit = 0).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("시작이 끝보다 늦으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenStartIsAfterEnd() {
            val response = createCampaign(startDate = now.plusDays(8), endDate = now.plusDays(1))

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("끝이 이미 지났으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenEndIsPast() {
            val response = createCampaign(startDate = now.minusDays(8), endDate = now.minusDays(1))

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("관리자가 아니면, 403 FORBIDDEN 응답을 받고 저장되지 않는다.")
        @Test
        fun returnsForbidden_whenNotAdmin() {
            val response = createCampaign(role = "USER")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
                { assertThat(campaignJpaRepository.count()).isZero() },
            )
        }
    }
}
