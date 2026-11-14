package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun saveAllNew(coupons: List<Coupon>): List<Coupon> = couponJpaRepository.saveAllAndFlush(coupons)

    override fun findAll(campaignId: Long, userIds: Collection<Long>): List<Coupon> =
        couponJpaRepository.findAllByCampaignIdAndUserIdIn(campaignId, userIds)

    override fun findAllByUserId(userId: Long): List<Coupon> =
        couponJpaRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId)
}
