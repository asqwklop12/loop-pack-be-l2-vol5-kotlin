package com.loopers.infrastructure.coupon

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.coupon.IssuedCouponCache
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class IssuedCouponRedisCache(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) private val redisTemplate: RedisTemplate<String, String>,
) : IssuedCouponCache {
    override fun findCouponId(campaignId: Long, userId: Long): Long? =
        redisTemplate.opsForHash<String, String>().get(key(campaignId), userId.toString())?.toLong()

    override fun save(campaignId: Long, userId: Long, couponId: Long) {
        redisTemplate.opsForHash<String, String>().put(key(campaignId), userId.toString(), couponId.toString())
    }

    companion object {
        fun key(campaignId: Long) = "campaign:$campaignId:issued"
    }
}
