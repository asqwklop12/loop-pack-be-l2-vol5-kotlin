package com.loopers.infrastructure.coupon

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.CouponIssuePermits
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CouponIssueRedisPermits(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${coupon.issue.permits:3}") private val permits: Long,
) : CouponIssuePermits {
    override fun tryAcquire(): String? {
        val token = UUID.randomUUID().toString()
        redisTemplate.opsForSet().add(KEY, token)
        val taken = try {
            redisTemplate.opsForSet().size(KEY) ?: Long.MAX_VALUE
        } catch (e: RuntimeException) {
            runCatching { release(token) }
            throw e
        }
        if (taken <= permits) return token
        release(token)
        return null
    }

    override fun release(token: String) {
        redisTemplate.opsForSet().remove(KEY, token)
    }

    companion object {
        const val KEY = "coupon:issue:permits"
    }
}
