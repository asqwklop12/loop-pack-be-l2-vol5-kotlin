package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.infrastructure.campaign.CampaignJpaRepository
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
import java.time.ZonedDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["coupon.issue.permits=30"])
class CouponIssueConcurrencyIntegrationTest @Autowired constructor(
    private val couponFacade: CouponFacade,
    private val campaignJpaRepository: CampaignJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("서로 다른 사용자가 한도보다 많이 동시에 요청하면, 한도만큼만 발급되고 나머지는 품절로 거절된다.")
    @Test
    fun issuesUpToLimit_whenUsersExceedLimitConcurrently() {
        val now = ZonedDateTime.now()
        val campaign = campaignJpaRepository.save(
            Campaign(name = "10% 할인", totalLimit = 5, startDate = now.minusDays(1), endDate = now.plusDays(7)),
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

        assertAll(
            { assertThat(finished).isTrue() },
            { assertThat(failed).isEmpty() },
            { assertThat(rejected).hasSize(users - 5) },
            { assertThat(rejected.map { it.errorType }).containsOnly(ErrorType.BAD_REQUEST) },
            { assertThat(couponJpaRepository.count()).isEqualTo(5) },
            { assertThat(campaignJpaRepository.findById(campaign.id).get().issuedCount).isEqualTo(5) },
        )
    }
}
