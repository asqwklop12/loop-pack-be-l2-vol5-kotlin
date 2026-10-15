package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductSearch
import com.loopers.domain.product.ProductSearchCache
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 정렬 결과를 Redis 에 담는다. 값은 상품 id 를 쉼표로 이은 문자열이다.
 *
 * `RedisConfig` 의 직렬화가 문자열이라 그대로 맞춘다. JSON 으로 감싸면 바이트만 늘고
 * 담는 것이 숫자 목록뿐이라 얻는 것이 없다.
 *
 * **Redis 가 죽어도 조회는 살아야 한다.** 모든 연산을 감싸서 실패하면 캐시가 없는 것처럼
 * 동작한다 — 읽기는 `null`(미스)로, 쓰기는 조용히 버린다. 호출자는 DB 로 간다.
 */
@Component
class ProductSearchRedisCache(
    private val redisTemplate: RedisTemplate<String, String>,
) : ProductSearchCache {
    override fun find(search: ProductSearch): List<Long>? {
        val raw = runCatching { redisTemplate.opsForValue().get(keyOf(search)) }
            .onFailure { logFailure("조회", it) }
            .getOrNull()
            ?: return null

        if (raw.isEmpty()) return emptyList()

        return runCatching { raw.split(DELIMITER).map { it.toLong() } }
            .onFailure { logFailure("해석", it) }
            .getOrNull()
    }

    override fun put(search: ProductSearch, productIds: List<Long>) {
        runCatching {
            redisTemplate.opsForValue().set(keyOf(search), productIds.joinToString(DELIMITER), TTL)
        }.onFailure { logFailure("저장", it) }
    }

    override fun evict(search: ProductSearch) {
        runCatching { redisTemplate.delete(keyOf(search)) }
            .onFailure { logFailure("삭제", it) }
    }

    /**
     * 조건이 하나라도 다르면 결과가 다르므로 키도 달라야 한다.
     * 브랜드를 주지 않은 전체 목록은 `all` 로 둔다 — `null` 을 문자열로 쓰면 브랜드명과 겹칠 수 있다.
     */
    private fun keyOf(search: ProductSearch): String =
        "product:search:${search.sort.value}:brand=${search.brandId ?: "all"}:page=${search.page}:size=${search.size}"

    /**
     * 원인까지 남긴다. 메시지만 남기면 `Redis exception` 한 줄로 끝나 무엇이 끊겼는지 알 수 없다.
     * 끊긴 동안 요청마다 찍히므로 `warn` 으로 두되 스택은 가장 안쪽 원인만 적는다.
     */
    private fun logFailure(what: String, cause: Throwable) {
        val root = generateSequence(cause) { it.cause }.last()
        log.warn(
            "상품 목록 캐시 {} 실패. DB 로 우회한다. [{}: {}] <- [{}: {}]",
            what,
            cause::class.simpleName,
            cause.message,
            root::class.simpleName,
            root.message,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProductSearchRedisCache::class.java)

        /**
         * 좋아요는 계속 들어오므로 순위도 계속 바뀐다. 무효화로 따라가면 좋아요마다 캐시를 버려야 해
         * 캐시가 거의 비어 있게 된다. 대신 **얼마나 지난 순위까지 보여줄지**를 이 값으로 정한다.
         */
        private val TTL: Duration = Duration.ofSeconds(60)
        private const val DELIMITER = ","
    }
}
