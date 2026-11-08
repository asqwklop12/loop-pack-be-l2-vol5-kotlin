package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun saveNew(coupon: Coupon): Coupon = couponJpaRepository.saveAndFlush(coupon)

    override fun find(campaignId: Long, userId: Long): Coupon? =
        couponJpaRepository.findByCampaignIdAndUserId(campaignId, userId)
}
