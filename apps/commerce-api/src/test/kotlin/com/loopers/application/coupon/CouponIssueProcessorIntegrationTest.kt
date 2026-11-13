package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.domain.coupon.Coupon
import com.loopers.infrastructure.campaign.CampaignIssueRedisCache
import com.loopers.infrastructure.campaign.CampaignJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.IssuedCouponRedisCache
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.ZonedDateTime

@SpringBootTest
class CouponIssueProcessorIntegrationTest @Autowired constructor(
    private val couponIssueProcessor: CouponIssueProcessor,
    private val campaignJpaRepository: CampaignJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun savedCampaign(totalLimit: Int): Campaign {
        val now = ZonedDateTime.now()
        return campaignJpaRepository.save(
            Campaign(name = "10% 할인", totalLimit = totalLimit, startDate = now.minusDays(1), endDate = now.plusDays(7)),
        )
    }

    private fun issuedCount(campaignId: Long) = campaignJpaRepository.findById(campaignId).get().issuedCount

    private fun recordedUsers(campaignId: Long) =
        redisTemplate.opsForHash<String, String>().keys(IssuedCouponRedisCache.key(campaignId)).map { it.toLong() }

    @DisplayName("묶음 요청을 한 번에 처리하면, 모두 발급되고 발급 수가 묶음 크기만큼 늘고 모두 발급 기록에 남는다.")
    @Test
    fun issuesWholeBatch_whenEnoughLeft() {
        val campaign = savedCampaign(totalLimit = 10)

        couponIssueProcessor.process(campaign.id, listOf(1L, 2L, 3L, 4L, 5L))

        assertAll(
            { assertThat(couponJpaRepository.findAll().map { it.userId }).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L) },
            { assertThat(issuedCount(campaign.id)).isEqualTo(5) },
            { assertThat(recordedUsers(campaign.id)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L) },
            { assertThat(redisTemplate.hasKey(CampaignIssueRedisCache.soldOutKey(campaign.id))).isFalse() },
        )
    }

    @DisplayName("남은 수량보다 묶음이 크면, 큐 순서대로 앞에서부터 남은 만큼만 발급하고 품절을 표시한다.")
    @Test
    fun issuesFromFrontUpToRemaining_whenBatchExceedsRemaining() {
        val campaign = savedCampaign(totalLimit = 3)

        couponIssueProcessor.process(campaign.id, listOf(5L, 4L, 3L, 2L, 1L))

        assertAll(
            { assertThat(couponJpaRepository.findAll().map { it.userId }).containsExactlyInAnyOrder(5L, 4L, 3L) },
            { assertThat(issuedCount(campaign.id)).isEqualTo(3) },
            { assertThat(redisTemplate.hasKey(CampaignIssueRedisCache.soldOutKey(campaign.id))).isTrue() },
        )
    }

    @DisplayName("이미 쿠폰을 가진 사용자가 섞여 있으면, 그 사용자는 빼고 발급하고 발급 수는 새로 받은 수만큼만 는다.")
    @Test
    fun skipsHolders_whenBatchContainsUsersWithCoupon() {
        val campaign = savedCampaign(totalLimit = 10)
        couponIssueProcessor.process(campaign.id, listOf(1L))
        redisCleanUp.truncateAll()

        couponIssueProcessor.process(campaign.id, listOf(1L, 2L, 3L))

        assertAll(
            { assertThat(couponJpaRepository.findAll().map(Coupon::userId)).containsExactlyInAnyOrder(1L, 2L, 3L) },
            { assertThat(issuedCount(campaign.id)).isEqualTo(3) },
            { assertThat(recordedUsers(campaign.id)).containsExactlyInAnyOrder(1L, 2L, 3L) },
        )
    }

    @DisplayName("묶음 안에 같은 사용자가 두 번 있어도, 쿠폰은 한 장만 발급된다.")
    @Test
    fun issuesOnce_whenSameUserTwiceInBatch() {
        val campaign = savedCampaign(totalLimit = 10)

        couponIssueProcessor.process(campaign.id, listOf(1L, 1L, 2L))

        assertAll(
            { assertThat(couponJpaRepository.findAll().map { it.userId }).containsExactlyInAnyOrder(1L, 2L) },
            { assertThat(issuedCount(campaign.id)).isEqualTo(2) },
        )
    }
}
