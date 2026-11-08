package com.loopers.domain.coupon

interface CouponRepository {
    fun saveNew(coupon: Coupon): Coupon
    fun find(campaignId: Long, userId: Long): Coupon?
    fun findAllByUserId(userId: Long): List<Coupon>
}
