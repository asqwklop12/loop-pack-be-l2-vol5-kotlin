package com.loopers.domain.coupon

interface IssuedCouponCache {
    fun findCouponId(campaignId: Long, userId: Long): Long?

    fun save(campaignId: Long, userId: Long, couponId: Long)
}
