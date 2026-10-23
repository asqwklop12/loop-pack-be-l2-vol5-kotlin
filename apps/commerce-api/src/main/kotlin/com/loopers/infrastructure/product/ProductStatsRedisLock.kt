package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStatsLock
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 인스턴스가 여럿이면 스케줄러가 전부 같은 시각에 깨어난다. 먼저 잡은 하나만 집계한다.
 *
 * Redis 가 죽어 있으면 **잡은 것으로 본다.** 못 잡은 것으로 처리하면 아무도 집계하지 않아
 * 하루치가 통째로 비는데, 중복 실행은 같은 결과를 다시 쓰는 것이라 손해가 작다.
 */
@Component
class ProductStatsRedisLock(
    private val redisTemplate: RedisTemplate<String, String>,
) : ProductStatsLock {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun tryAcquire(): Boolean =
        runCatching {
            redisTemplate.opsForValue().setIfAbsent(KEY, LOCKED, TTL) == true
        }.onFailure { log.warn("집계 잠금 실패. Redis 없이 진행한다. cause={}", it.message) }
            .getOrDefault(true)

    override fun release() {
        runCatching { redisTemplate.delete(KEY) }
            .onFailure { log.warn("집계 잠금 해제 실패. cause={}", it.message) }
    }

    companion object {
        private const val KEY = "product:stats:refresh:lock"
        private const val LOCKED = "1"

        /** 집계가 끝나기 전에 풀리면 중복 실행이 된다. 실측 0.2초보다 크게 잡는다. */
        private val TTL: Duration = Duration.ofMinutes(10)
    }
}
