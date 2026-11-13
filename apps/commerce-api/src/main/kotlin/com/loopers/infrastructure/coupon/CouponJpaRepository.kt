package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<Coupon, Long> {
    fun findAllByCampaignIdAndUserIdIn(campaignId: Long, userIds: Collection<Long>): List<Coupon>
    fun findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId: Long): List<Coupon>
}
