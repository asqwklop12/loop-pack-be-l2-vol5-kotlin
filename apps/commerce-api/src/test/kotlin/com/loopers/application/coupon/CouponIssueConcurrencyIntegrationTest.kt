package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.infrastructure.campaign.CampaignIssueRedisCache
import com.loopers.infrastructure.campaign.CampaignJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import com.loopers.utils.awaitCouponIssueQueueDrained
import com.loopers.utils.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.ZonedDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class CouponIssueConcurrencyIntegrationTest @Autowired constructor(
    private val couponFacade: CouponFacade,
    private val campaignJpaRepository: CampaignJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        try {
            redisTemplate.awaitCouponIssueQueueDrained()
        } finally {
            databaseCleanUp.truncateAllTables()
            redisCleanUp.truncateAll()
        }
    }

    private fun savedCampaign(totalLimit: Int): Campaign {
        val now = ZonedDateTime.now()
        return campaignJpaRepository.save(
            Campaign(name = "10% 할인", totalLimit = totalLimit, startDate = now.minusDays(1), endDate = now.plusDays(7)),
        )
    }

    private fun runConcurrently(times: Int, block: (Int) -> CouponIssueResult): Pair<List<CouponIssueResult>, List<Throwable>> {
        val start = CountDownLatch(1)
        val done = CountDownLatch(times)
        val results = Collections.synchronizedList(mutableListOf<CouponIssueResult>())
        val failed = Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(times)
        repeat(times) { index ->
            pool.submit {
                try {
                    start.await()
                    results.add(block(index))
                } catch (e: Throwable) {
                    failed.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        return results to failed
    }

    @DisplayName("서로 다른 사용자가 한도보다 많이 동시에 요청하면, 모두 접수되고 워커가 한도만큼만 발급한 뒤 품절을 표시한다.")
    @Test
    fun issuesUpToLimit_whenUsersExceedLimitConcurrently() {
        val campaign = savedCampaign(totalLimit = 5)
        val users = 30

        val (results, failed) = runConcurrently(users) { couponFacade.issue(campaign.id, userId = it + 1L) }

        assertAll(
            { assertThat(failed).isEmpty() },
            { assertThat(results).hasSize(users).allMatch { it is CouponIssueResult.Accepted } },
        )
        redisTemplate.awaitCouponIssueQueueDrained()
        assertAll(
            { assertThat(couponJpaRepository.count()).isEqualTo(5) },
            { assertThat(campaignJpaRepository.findById(campaign.id).get().issuedCount).isEqualTo(5) },
            { assertThat(redisTemplate.hasKey(CampaignIssueRedisCache.soldOutKey(campaign.id))).isTrue() },
        )
    }

    @DisplayName("같은 사용자가 동시에 여러 번 요청해도, 쿠폰은 한 장만 발급된다.")
    @Test
    fun issuesOnce_whenSameUserRequestsConcurrently() {
        val campaign = savedCampaign(totalLimit = 100)

        val (_, failed) = runConcurrently(10) { couponFacade.issue(campaign.id, userId = 1L) }

        assertThat(failed).isEmpty()
        eventually { assertThat(couponJpaRepository.count()).isEqualTo(1) }
        redisTemplate.awaitCouponIssueQueueDrained()
        assertAll(
            { assertThat(couponJpaRepository.count()).isEqualTo(1) },
            { assertThat(campaignJpaRepository.findById(campaign.id).get().issuedCount).isEqualTo(1) },
        )
    }
}
