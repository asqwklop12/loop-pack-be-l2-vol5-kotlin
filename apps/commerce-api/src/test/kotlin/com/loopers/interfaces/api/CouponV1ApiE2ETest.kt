package com.loopers.interfaces.api

import com.loopers.domain.campaign.Campaign
import com.loopers.infrastructure.campaign.CampaignJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.interfaces.api.coupon.CouponV1Dto
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
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val campaignJpaRepository: CampaignJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private val now = ZonedDateTime.now()

    private val couponType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>>() {}

    private fun savedCampaign(
        totalLimit: Int = 100,
        startDate: ZonedDateTime = now.minusDays(1),
        endDate: ZonedDateTime = now.plusDays(7),
    ) = campaignJpaRepository.save(
        Campaign(name = "10% 할인", totalLimit = totalLimit, startDate = startDate, endDate = endDate),
    )

    private fun issue(campaignId: Long, userId: Long = 1L) = testRestTemplate.exchange(
        "/api/v1/coupon-campaigns/$campaignId/issues",
        HttpMethod.POST,
        HttpEntity<Any>(HttpHeaders().apply { set("X-USER-ID", userId.toString()) }),
        couponType,
    )

    private fun issuedCount(campaignId: Long) = campaignJpaRepository.findById(campaignId).get().issuedCount

    @DisplayName("POST /api/v1/coupon-campaigns/{campaignId}/issues")
    @Nested
    inner class Issue {
        @DisplayName("처음 발급이면, 201 CREATED 와 쿠폰을 받고 캠페인 발급 수가 1 늘어난다.")
        @Test
        fun returnsCreated_whenFirstIssue() {
            val campaign = savedCampaign()

            val response = issue(campaign.id, userId = 7L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.campaignId).isEqualTo(campaign.id) },
                { assertThat(response.body?.data?.userId).isEqualTo(7L) },
                { assertThat(couponJpaRepository.count()).isEqualTo(1) },
                { assertThat(issuedCount(campaign.id)).isEqualTo(1) },
            )
        }

        @DisplayName("캠페인이 존재하지 않으면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenCampaignDoesNotExist() {
            assertThat(issue(campaignId = 999L).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("한정 수량이 다 나갔으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenSoldOut() {
            val campaign = savedCampaign(totalLimit = 1).also { issue(it.id, userId = 1L) }

            val response = issue(campaign.id, userId = 2L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("Bad Request") },
                { assertThat(couponJpaRepository.count()).isEqualTo(1) },
            )
        }

        @DisplayName("이미 발급받았으면, 200 OK 와 같은 couponId 를 받고 발급 수가 늘지 않는다.")
        @Test
        fun returnsOkWithSameCoupon_whenAlreadyIssued() {
            val campaign = savedCampaign()
            val first = issue(campaign.id, userId = 1L).body?.data?.id

            val response = issue(campaign.id, userId = 1L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(first) },
                { assertThat(couponJpaRepository.count()).isEqualTo(1) },
                { assertThat(issuedCount(campaign.id)).isEqualTo(1) },
            )
        }

        @DisplayName("이미 발급받았으면, 그 뒤 품절이어도 200 OK 와 같은 couponId 를 받는다.")
        @Test
        fun returnsOkWithSameCoupon_whenAlreadyIssuedAndSoldOut() {
            val campaign = savedCampaign(totalLimit = 1)
            val first = issue(campaign.id, userId = 1L).body?.data?.id

            val response = issue(campaign.id, userId = 1L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(first) },
            )
        }

        @DisplayName("쿼리로 다른 userId 를 보내도, 헤더의 사용자에게 발급된다.")
        @Test
        fun issuesToHeaderUser_whenQueryHasOtherUserId() {
            val campaign = savedCampaign()

            val response = testRestTemplate.exchange(
                "/api/v1/coupon-campaigns/${campaign.id}/issues?userId=2",
                HttpMethod.POST,
                HttpEntity<Any>(HttpHeaders().apply { set("X-USER-ID", "1") }),
                couponType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.userId).isEqualTo(1L) },
            )
        }

        @DisplayName("발급 기간이 지났으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenPeriodEnded() {
            val campaign = savedCampaign()
            jdbcTemplate.update(
                "update campaign set start_date = ?, end_date = ? where id = ?",
                now.minusDays(8).toLocalDateTime(),
                now.minusDays(1).toLocalDateTime(),
                campaign.id,
            )

            assertThat(issue(campaign.id).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("발급 불가 상태면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNotIssuable() {
            val campaign = savedCampaign()
            jdbcTemplate.update("update campaign set issuable = false where id = ?", campaign.id)

            val response = issue(campaign.id)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("Bad Request") },
            )
        }

        @DisplayName("발급 기간이 아직 시작 전이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenPeriodNotStarted() {
            val campaign = savedCampaign(startDate = now.plusDays(1))

            val response = issue(campaign.id)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(couponJpaRepository.count()).isZero() },
                { assertThat(issuedCount(campaign.id)).isZero() },
            )
        }
    }

    private val couponListType =
        object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.Response>>>() {}

    private fun getMyCoupons(userId: Long) = testRestTemplate.exchange(
        "/api/v1/coupons",
        HttpMethod.GET,
        HttpEntity<Any>(HttpHeaders().apply { set("X-USER-ID", userId.toString()) }),
        couponListType,
    )

    @DisplayName("GET /api/v1/coupons")
    @Nested
    inner class GetMyCoupons {
        @DisplayName("현재 사용자가 받은 쿠폰만, 발급 시각 내림차순으로 반환한다.")
        @Test
        fun returnsOnlyCurrentUsersCouponsLatestFirst() {
            val first = savedCampaign()
            val second = savedCampaign()
            issue(first.id, userId = 1L)
            issue(second.id, userId = 1L)
            issue(first.id, userId = 2L)

            val response = getMyCoupons(userId = 1L)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.map { it.campaignId }).containsExactly(second.id, first.id) },
                { assertThat(response.body?.data?.map { it.userId }).containsOnly(1L) },
            )
        }

        @DisplayName("발급 시각이 같으면, couponId 내림차순으로 반환한다.")
        @Test
        fun returnsLargerCouponIdFirst_whenIssuedAtIsSame() {
            val first = issue(savedCampaign().id, userId = 1L).body!!.data!!.id
            val second = issue(savedCampaign().id, userId = 1L).body!!.data!!.id
            jdbcTemplate.update("update coupon set created_at = ? where user_id = 1", now.toLocalDateTime())

            val response = getMyCoupons(userId = 1L)

            assertThat(response.body?.data?.map { it.id }).containsExactly(second, first)
        }

        @DisplayName("받은 쿠폰이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmpty_whenNoCoupons() {
            val response = getMyCoupons(userId = 1L)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data).isEmpty() },
            )
        }
    }
}
