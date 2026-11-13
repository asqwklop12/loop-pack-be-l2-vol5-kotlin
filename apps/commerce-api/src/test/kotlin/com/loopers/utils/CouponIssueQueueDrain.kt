package com.loopers.utils

import com.loopers.infrastructure.coupon.CouponIssueRedisQueue
import org.assertj.core.api.Assertions.assertThat
import org.springframework.data.redis.core.RedisTemplate

private const val IN_FLIGHT_GRACE_MILLIS = 300L

fun RedisTemplate<String, String>.awaitCouponIssueQueueDrained() {
    eventually { assertThat(opsForList().size(CouponIssueRedisQueue.KEY) ?: 0L).isZero() }
    Thread.sleep(IN_FLIGHT_GRACE_MILLIS)
}
