package com.loopers.domain.coupon

import java.time.Duration

interface CouponIssueQueue {
    fun enqueue(request: CouponIssueRequest)

    fun take(timeout: Duration): CouponIssueRequest?
}
