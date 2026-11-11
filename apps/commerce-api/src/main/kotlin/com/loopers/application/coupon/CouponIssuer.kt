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
    fun issueUnlessSoldOut(campaignId: Long, userId: Long): Coupon? {
        if (!campaignService.tryIssue(campaignId)) return null
        return couponService.issue(campaignId, userId)
    }
}
