package com.loopers.domain.coupon

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponService(
    private val couponRepository: CouponRepository,
) {

    fun issue(campaignId: Long, userId: Long): Coupon =
        couponRepository.saveNew(Coupon(campaignId = campaignId, userId = userId))

    @Transactional(readOnly = true)
    fun find(campaignId: Long, userId: Long): Coupon? = couponRepository.find(campaignId, userId)
}
