package com.loopers.domain.coupon

interface CouponRepository {
    fun saveAllNew(coupons: List<Coupon>): List<Coupon>
    fun findAll(campaignId: Long, userIds: Collection<Long>): List<Coupon>
    fun findAllByUserId(userId: Long): List<Coupon>
}
