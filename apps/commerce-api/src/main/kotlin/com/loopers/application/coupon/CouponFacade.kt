package com.loopers.application.coupon

import com.loopers.domain.campaign.Campaign
import com.loopers.domain.campaign.CampaignIssueCache
import com.loopers.domain.campaign.CampaignService
import com.loopers.domain.coupon.CouponIssueQueue
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.IssuedCouponCache
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CouponFacade(
    private val campaignService: CampaignService,
    private val couponService: CouponService,
    private val campaignIssueCache: CampaignIssueCache,
    private val issuedCouponCache: IssuedCouponCache,
    private val couponIssueQueue: CouponIssueQueue,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun issue(campaignId: Long, userId: Long): CouponIssueResult {
        skipIfRedisDown("기존 쿠폰 확인") { issuedCouponCache.findCouponId(campaignId, userId) }
            ?.let { return CouponIssueResult.Issued(CouponInfo(id = it, campaignId = campaignId, userId = userId)) }

        if (skipIfRedisDown("품절 확인") { campaignIssueCache.isSoldOut(campaignId) } == true) throw Campaign.soldOut()

        checkIssuance(campaignId)

        enqueue(CouponIssueRequest(campaignId = campaignId, userId = userId))
        return CouponIssueResult.Accepted(campaignId = campaignId, userId = userId)
    }

    fun getMyCoupons(userId: Long): List<CouponInfo> = couponService.getAllByUserId(userId).map { CouponInfo.from(it) }

    private fun checkIssuance(campaignId: Long) {
        val cached = try {
            campaignIssueCache.findIssuance(campaignId)
        } catch (e: DataAccessException) {
            log.warn("캠페인 기간 확인을 건너뛴다. campaignId={}, cause={}", campaignId, e.message)
            return
        }
        val issuance = cached ?: campaignService.getIssuance(campaignId).also {
            skipIfRedisDown("캠페인 기간 저장") { campaignIssueCache.saveIssuance(campaignId, it) }
        }
        issuance.check(ZonedDateTime.now())
    }

    private fun enqueue(request: CouponIssueRequest) {
        try {
            couponIssueQueue.enqueue(request)
        } catch (e: DataAccessException) {
            log.warn("발급 요청을 큐에 넣지 못했다. request={}, cause={}", request, e.message)
            throw CoreException(ErrorType.SERVICE_UNAVAILABLE, "지금은 쿠폰 발급 요청을 받을 수 있는지 확인할 수 없습니다.")
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
