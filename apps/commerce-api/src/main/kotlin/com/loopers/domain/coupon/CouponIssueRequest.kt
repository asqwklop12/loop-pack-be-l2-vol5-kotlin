package com.loopers.domain.coupon

data class CouponIssueRequest(
    val campaignId: Long,
    val userId: Long,
)
