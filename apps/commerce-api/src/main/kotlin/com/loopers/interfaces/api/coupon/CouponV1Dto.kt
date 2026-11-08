package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponInfo

class CouponV1Dto {
    data class Response(
        val id: Long,
        val campaignId: Long,
        val userId: Long,
    ) {
        companion object {
            fun from(info: CouponInfo) = Response(id = info.id, campaignId = info.campaignId, userId = info.userId)
        }
    }
}
