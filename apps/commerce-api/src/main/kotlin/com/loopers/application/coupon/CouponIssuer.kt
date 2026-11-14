package com.loopers.application.coupon

import com.loopers.domain.campaign.CampaignService
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssuer(
    private val campaignService: CampaignService,
    private val couponService: CouponService,
) {
    @Transactional
    fun issueAll(campaignId: Long, userIds: List<Long>): CouponBatchIssue {
        val requested = userIds.distinct()
        val existing = couponService.findAll(campaignId, requested)
        val holders = existing.map { it.userId }.toSet()
        val newcomers = requested.filterNot { it in holders }
        if (newcomers.isEmpty()) return CouponBatchIssue(coupons = existing, soldOut = false)

        val granted = campaignService.issue(campaignId, newcomers.size)
        val issued = couponService.issueAll(campaignId, newcomers.take(granted))
        return CouponBatchIssue(coupons = existing + issued, soldOut = granted < newcomers.size)
    }
}

data class CouponBatchIssue(
    val coupons: List<Coupon>,
    val soldOut: Boolean,
)
