package com.loopers.application.coupon

sealed interface CouponIssueResult {
    data class Issued(val coupon: CouponInfo) : CouponIssueResult

    data class Accepted(val campaignId: Long, val userId: Long) : CouponIssueResult
}
