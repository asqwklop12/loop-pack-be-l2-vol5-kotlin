package com.loopers.domain.coupon

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponService(
    private val couponRepository: CouponRepository,
) {

    fun issueAll(campaignId: Long, userIds: List<Long>): List<Coupon> =
        couponRepository.saveAllNew(userIds.map { Coupon(campaignId = campaignId, userId = it) })

    fun findAll(campaignId: Long, userIds: Collection<Long>): List<Coupon> = couponRepository.findAll(campaignId, userIds)

    @Transactional(readOnly = true)
    fun getAllByUserId(userId: Long): List<Coupon> = couponRepository.findAllByUserId(userId)
}
