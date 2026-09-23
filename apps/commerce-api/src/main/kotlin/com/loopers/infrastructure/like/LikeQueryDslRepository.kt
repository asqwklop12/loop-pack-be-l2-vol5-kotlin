package com.loopers.infrastructure.like

import com.loopers.domain.like.QLike
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

@Component
class LikeQueryDslRepository(
    private val queryFactory: JPAQueryFactory,
) {
    private val like = QLike.like

    /**
     * 여러 상품의 좋아요 수를 한 번에 센다. 목록 조회에서 상품마다 세지 않기 위해서다.
     * 좋아요가 없는 상품은 결과에 없으므로 호출자가 0 으로 채운다.
     */
    fun countByProductIds(productIds: List<Long>): Map<Long, Long> {
        if (productIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(like.productId, like.count())
            .from(like)
            .where(like.productId.`in`(productIds))
            .groupBy(like.productId)
            .fetch()
            .associate { (it.get(like.productId) ?: 0L) to (it.get(like.count()) ?: 0L) }
    }
}
