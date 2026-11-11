package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.domain.campaign.CampaignIssueCache
import com.loopers.domain.coupon.CouponIssuePermits
import com.loopers.domain.coupon.IssuedCouponCache
import com.loopers.infrastructure.campaign.CampaignJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

@SpringBootTest
class CouponIssueRedisFailureIntegrationTest @Autowired constructor(
    private val couponFacade: CouponFacade,
    private val campaignJpaRepository: CampaignJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var issuedCouponCache: IssuedCouponCache

    @MockkBean
    private lateinit var campaignIssueCache: CampaignIssueCache

    @MockkBean
    private lateinit var couponIssuePermits: CouponIssuePermits

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private val redisDown = RedisConnectionFailureException("redis down")

    private fun savedCampaign(): Campaign {
        val now = ZonedDateTime.now()
        return campaignJpaRepository.save(
            Campaign(name = "10% 할인", totalLimit = 100, startDate = now.minusDays(1), endDate = now.plusDays(7)),
        )
    }

    private fun cachesDown() {
        every { issuedCouponCache.findCouponId(any(), any()) } throws redisDown
        every { issuedCouponCache.save(any(), any(), any()) } throws redisDown
        every { campaignIssueCache.isSoldOut(any()) } throws redisDown
        every { campaignIssueCache.markSoldOut(any()) } throws redisDown
        every { campaignIssueCache.findIssuance(any()) } throws redisDown
        every { campaignIssueCache.saveIssuance(any(), any()) } throws redisDown
    }

    @DisplayName("기존 쿠폰·품절·기간 확인에서 Redis 가 죽어 있으면, 건너뛰고 DB 에서 발급한다.")
    @Test
    fun issuesFromDatabase_whenCachesAreDown() {
        val campaign = savedCampaign()
        cachesDown()
        every { couponIssuePermits.tryAcquire() } returns "token"
        every { couponIssuePermits.release(any()) } returns Unit

        val result = couponFacade.issue(campaign.id, userId = 1L)

        assertAll(
            { assertThat(result.newlyIssued).isTrue() },
            { assertThat(couponJpaRepository.count()).isEqualTo(1) },
        )
    }

    @DisplayName("Redis 가 죽어 있어도 기간이 지난 캠페인은, 발급 트랜잭션에서 400 으로 거절된다.")
    @Test
    fun rejectsInTransaction_whenCachesAreDownAndPeriodEnded() {
        val campaign = savedCampaign()
        cachesDown()
        every { couponIssuePermits.tryAcquire() } returns "token"
        every { couponIssuePermits.release(any()) } returns Unit
        val now = ZonedDateTime.now()
        jdbcTemplate.update(
            "update campaign set start_date = ?, end_date = ? where id = ?",
            now.minusDays(8).toLocalDateTime(),
            now.minusDays(1).toLocalDateTime(),
            campaign.id,
        )

        val exception = assertThrows<CoreException> { couponFacade.issue(campaign.id, userId = 1L) }

        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            { assertThat(couponJpaRepository.count()).isZero() },
        )
    }

    @DisplayName("발급 자리를 확인할 때 Redis 가 죽어 있으면, 503 으로 거절하고 DB 에서 발급하지 않는다.")
    @Test
    fun rejectsWithServiceUnavailable_whenPermitsAreDown() {
        val campaign = savedCampaign()
        cachesDown()
        every { couponIssuePermits.tryAcquire() } throws redisDown

        val exception = assertThrows<CoreException> { couponFacade.issue(campaign.id, userId = 1L) }

        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.SERVICE_UNAVAILABLE) },
            { assertThat(couponJpaRepository.count()).isZero() },
            { assertThat(campaignJpaRepository.findById(campaign.id).get().issuedCount).isZero() },
        )
    }

    @DisplayName("토큰 반환에서 Redis 가 죽어 있어도, 발급한 쿠폰은 유지된다.")
    @Test
    fun keepsCoupon_whenReleaseFails() {
        val campaign = savedCampaign()
        cachesDown()
        every { couponIssuePermits.tryAcquire() } returns "token"
        every { couponIssuePermits.release(any()) } throws redisDown

        val result = couponFacade.issue(campaign.id, userId = 1L)

        assertAll(
            { assertThat(result.newlyIssued).isTrue() },
            { assertThat(couponJpaRepository.count()).isEqualTo(1) },
        )
    }
}
