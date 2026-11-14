package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponInfo
import com.loopers.application.coupon.CouponIssueResult

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

    data class Accepted(
        val campaignId: Long,
        val userId: Long,
    ) {
        companion object {
            fun from(result: CouponIssueResult.Accepted) = Accepted(campaignId = result.campaignId, userId = result.userId)
        }
    }
}
