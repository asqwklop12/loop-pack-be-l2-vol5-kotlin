package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.infrastructure.campaign.CampaignJpaRepository
import com.loopers.infrastructure.coupon.CouponIssueRedisPermits
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class CouponIssuePermitsConcurrencyIntegrationTest @Autowired constructor(
    private val couponFacade: CouponFacade,
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

    @DisplayName("발급 자리보다 많이 동시에 요청하면, 자리를 못 잡은 요청만 429 로 거절되고 끝나면 토큰이 모두 반환된다.")
    @Test
    fun rejectsOnlyWithTooManyRequests_andReturnsAllTokens() {
        val now = ZonedDateTime.now()
        val campaign = campaignJpaRepository.save(
            Campaign(name = "10% 할인", totalLimit = 100, startDate = now.minusDays(1), endDate = now.plusDays(7)),
        )
        val users = 30
        val start = CountDownLatch(1)
        val done = CountDownLatch(users)
        val rejected = Collections.synchronizedList(mutableListOf<CoreException>())
        val failed = Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(users)

        repeat(users) { index ->
            pool.submit {
                try {
                    start.await()
                    couponFacade.issue(campaign.id, userId = index + 1L)
                } catch (e: CoreException) {
                    rejected.add(e)
                } catch (e: Throwable) {
                    failed.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        val finished = done.await(30, TimeUnit.SECONDS)
        pool.shutdown()

        val coupons = couponJpaRepository.count()
        assertAll(
            { assertThat(finished).isTrue() },
            { assertThat(failed).isEmpty() },
            { assertThat(rejected.map { it.errorType }).allMatch { it == ErrorType.TOO_MANY_REQUESTS } },
            { assertThat(coupons + rejected.size).isEqualTo(users.toLong()) },
            { assertThat(campaignJpaRepository.findById(campaign.id).get().issuedCount.toLong()).isEqualTo(coupons) },
            { assertThat(redisTemplate.opsForSet().size(CouponIssueRedisPermits.KEY) ?: 0L).isZero() },
        )
    }
}
