package com.loopers.application.coupon

import com.loopers.domain.campaign.CampaignIssueCache
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponIssueQueue
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.IssuedCouponCache
import com.loopers.support.error.CoreException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CouponIssueProcessor(
    private val couponIssueQueue: CouponIssueQueue,
    private val couponIssuer: CouponIssuer,
    private val couponService: CouponService,
    private val campaignIssueCache: CampaignIssueCache,
    private val issuedCouponCache: IssuedCouponCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processNext(timeout: Duration): Boolean {
        val request = couponIssueQueue.take(timeout) ?: return false
        process(request)
        return true
    }

    fun process(request: CouponIssueRequest) {
        val coupon = try {
            couponIssuer.issueUnlessSoldOut(request.campaignId, request.userId)
        } catch (e: DataIntegrityViolationException) {
            couponService.find(request.campaignId, request.userId) ?: throw e
        } catch (e: CoreException) {
            log.info("발급하지 않는다. request={}, reason={}", request, e.message)
            return
        }

        if (coupon == null) {
            skipIfRedisDown("품절 표시") { campaignIssueCache.markSoldOut(request.campaignId) }
            return
        }
        record(coupon)
    }

    private fun record(coupon: Coupon) {
        skipIfRedisDown("발급 기록") { issuedCouponCache.save(coupon.campaignId, coupon.userId, coupon.id) }
    }

    private fun <T> skipIfRedisDown(step: String, block: () -> T): T? =
        try {
            block()
        } catch (e: DataAccessException) {
            log.warn("{}을 건너뛴다. cause={}", step, e.message)
            null
        }
}
