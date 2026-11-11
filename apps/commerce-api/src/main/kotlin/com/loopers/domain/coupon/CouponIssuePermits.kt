package com.loopers.domain.coupon

interface CouponIssuePermits {
    fun tryAcquire(): String?

    fun release(token: String)
}
