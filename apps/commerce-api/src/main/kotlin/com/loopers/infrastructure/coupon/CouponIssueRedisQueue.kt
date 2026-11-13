package com.loopers.infrastructure.coupon

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.CouponIssueQueue
import com.loopers.domain.coupon.CouponIssueRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CouponIssueRedisQueue(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
) : CouponIssueQueue {
    override fun enqueue(request: CouponIssueRequest) {
        redisTemplate.opsForList().leftPush(KEY, encode(request))
    }

    override fun take(timeout: Duration): CouponIssueRequest? = redisTemplate.opsForList().rightPop(KEY, timeout)?.let(::decode)

    private fun encode(request: CouponIssueRequest) = "${request.campaignId}:${request.userId}"

    private fun decode(value: String): CouponIssueRequest {
        val (campaignId, userId) = value.split(":")
        return CouponIssueRequest(campaignId = campaignId.toLong(), userId = userId.toLong())
    }

    companion object {
        const val KEY = "coupon:issue:queue"
    }
}
