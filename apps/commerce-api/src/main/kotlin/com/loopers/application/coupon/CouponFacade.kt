package com.loopers.application.coupon

import com.loopers.domain.campaign.CampaignService
import com.loopers.domain.coupon.CouponService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Component
class CouponFacade(
    private val campaignService: CampaignService,
    private val couponService: CouponService,
    private val transactionTemplate: TransactionTemplate,
) {
    @Transactional
    fun issue(campaignId: Long, userId: Long): CouponIssueResult {
        couponService.find(campaignId, userId)?.let { return existing(CouponInfo.from(it)) }

        return try {
            val coupon = transactionTemplate.execute {
                campaignService.issue(campaignId)
                couponService.issue(campaignId, userId)
            }!!
            CouponIssueResult(coupon = CouponInfo.from(coupon), newlyIssued = true)
        } catch (e: DataIntegrityViolationException) {
            val coupon = couponService.find(campaignId, userId) ?: throw e
            existing(CouponInfo.from(coupon))
        }
    }

    private fun existing(coupon: CouponInfo) = CouponIssueResult(coupon = coupon, newlyIssued = false)
}
