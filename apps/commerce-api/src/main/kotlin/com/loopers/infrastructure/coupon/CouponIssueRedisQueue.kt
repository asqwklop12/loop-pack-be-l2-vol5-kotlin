package com.loopers.infrastructure.coupon

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.CouponIssueQueue
import com.loopers.domain.coupon.CouponIssueRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CouponIssueRedisQueue(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
) : CouponIssueQueue {
    override fun enqueue(request: CouponIssueRequest) {
        val firstRequest = redisTemplate.opsForSet().add(requestedKey(request.campaignId), request.userId.toString()) == 1L
        if (!firstRequest) return
        try {
            redisTemplate.opsForList().leftPush(KEY, encode(request))
        } catch (e: DataAccessException) {
            runCatching { redisTemplate.opsForSet().remove(requestedKey(request.campaignId), request.userId.toString()) }
            throw e
        }
    }

    override fun take(timeout: Duration, max: Int): List<CouponIssueRequest> {
        val first = redisTemplate.opsForList().rightPop(KEY, timeout) ?: return emptyList()
        val rest = if (max > 1) redisTemplate.opsForList().rightPop(KEY, (max - 1).toLong()).orEmpty() else emptyList()
        return (listOf(first) + rest).map(::decode)
    }

    private fun encode(request: CouponIssueRequest) = "${request.campaignId}:${request.userId}"

    private fun decode(value: String): CouponIssueRequest {
        val (campaignId, userId) = value.split(":")
        return CouponIssueRequest(campaignId = campaignId.toLong(), userId = userId.toLong())
    }

    companion object {
        const val KEY = "coupon:issue:queue"

        fun requestedKey(campaignId: Long) = "campaign:$campaignId:requested"
    }
}
