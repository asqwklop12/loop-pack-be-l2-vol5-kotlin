package com.loopers.application.coupon

data class CouponIssueResult(
    val coupon: CouponInfo,
    val newlyIssued: Boolean,
)
