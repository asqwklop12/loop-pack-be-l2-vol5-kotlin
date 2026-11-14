package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon

data class CouponInfo(
    val id: Long,
    val campaignId: Long,
    val userId: Long,
) {
    companion object {
        fun from(coupon: Coupon): CouponInfo {
            return CouponInfo(
                id = coupon.id,
                campaignId = coupon.campaignId,
                userId = coupon.userId,
            )
        }
    }
}
