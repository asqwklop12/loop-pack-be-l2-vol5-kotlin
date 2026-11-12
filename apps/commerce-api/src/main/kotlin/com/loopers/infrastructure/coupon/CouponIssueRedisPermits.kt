package com.loopers.infrastructure.coupon

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.CouponIssuePermits
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CouponIssueRedisPermits(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${coupon.issue.permits:3}") private val permits: Long,
) : CouponIssuePermits {
    override fun tryAcquire(): String? {
        val token = UUID.randomUUID().toString()
        val acquired = redisTemplate.execute(ACQUIRE, listOf(KEY), token, permits.toString())
        return if (acquired == 1L) token else null
    }

    override fun release(token: String) {
        redisTemplate.opsForSet().remove(KEY, token)
    }

    companion object {
        const val KEY = "coupon:issue:permits"

        private val ACQUIRE: RedisScript<Long> = RedisScript.of(
            """
            if redis.call('SCARD', KEYS[1]) < tonumber(ARGV[2]) then
              redis.call('SADD', KEYS[1], ARGV[1])
              return 1
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
