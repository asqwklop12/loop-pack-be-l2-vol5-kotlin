package com.loopers.application.coupon

import com.loopers.domain.campaign.CampaignIssueCache
import com.loopers.domain.coupon.CouponIssueQueue
import com.loopers.domain.coupon.IssuedCouponCache
import com.loopers.support.error.CoreException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CouponIssueProcessor(
    private val couponIssueQueue: CouponIssueQueue,
    private val couponIssuer: CouponIssuer,
    private val campaignIssueCache: CampaignIssueCache,
    private val issuedCouponCache: IssuedCouponCache,
    @Value("\${coupon.issue.batch-size:50}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processNext(timeout: Duration): Boolean {
        val requests = couponIssueQueue.take(timeout, batchSize)
        if (requests.isEmpty()) return false
        requests.groupBy({ it.campaignId }, { it.userId }).forEach { (campaignId, userIds) -> process(campaignId, userIds) }
        return true
    }

    fun process(campaignId: Long, userIds: List<Long>) {
        val result = try {
            couponIssuer.issueAll(campaignId, userIds)
        } catch (e: CoreException) {
            log.info("발급하지 않는다. campaignId={}, users={}, reason={}", campaignId, userIds.size, e.message)
            return
        }

        if (result.soldOut) skipIfRedisDown("품절 표시") { campaignIssueCache.markSoldOut(campaignId) }
        result.coupons.forEach { coupon ->
            skipIfRedisDown("발급 기록") { issuedCouponCache.save(coupon.campaignId, coupon.userId, coupon.id) }
        }
    }

    private fun <T> skipIfRedisDown(step: String, block: () -> T): T? =
        try {
            block()
        } catch (e: DataAccessException) {
            log.warn("{}을 건너뛴다. cause={}", step, e.message)
            null
        }
}
