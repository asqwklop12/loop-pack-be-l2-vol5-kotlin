package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSearch
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.product.QProduct
import com.loopers.domain.product.QProductStats
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

@Component
class ProductQueryDslRepository(
    private val queryFactory: JPAQueryFactory,
) {
    private val product = QProduct.product
    private val productStats = QProductStats.productStats

    fun search(search: ProductSearch): List<Product> {
        return when (search.sort) {
            ProductSort.LIKES_DESC -> searchByLikes(search)
            else -> searchByColumn(search)
        }
    }

    fun countBy(brandId: Long?): Long {
        return queryFactory
            .select(product.count())
            .from(product)
            .where(notDeleted(), selling(), brandEq(brandId))
            .fetchOne() ?: 0L
    }

    private fun searchByColumn(search: ProductSearch): List<Product> {
        val query = queryFactory
            .selectFrom(product)
            .where(notDeleted(), selling(), brandEq(search.brandId))

        val ordered = when (search.sort) {
            ProductSort.PRICE_ASC -> query.orderBy(product.price.amount.asc(), product.id.desc())
            else -> query.orderBy(product.createdAt.desc(), product.id.desc())
        }

        return ordered
            .offset(search.offset)
            .limit(search.size.toLong())
            .fetch()
    }

    /**
     * 좋아요 수는 하루 한 번 집계한 `product_stats` 에서 읽는다. 관계를 세지 않는다.
     *
     * 좋아요가 한 번도 없는 상품은 집계에 행이 없으므로 left join 이고, 정렬에서는
     * 0 으로 본다. 집계를 쓴다고 이 쿼리가 빨라지지는 않는다 — 느린 이유는 세는 것이
     * 아니라 판매중 상품 전체를 정렬하는 것이라, 상위 구간은 캐시가 계속 맡는다.
     */
    private fun searchByLikes(search: ProductSearch): List<Product> {
        return queryFactory
            .selectFrom(product)
            .leftJoin(productStats).on(productStats.productId.eq(product.id))
            .where(notDeleted(), selling(), brandEq(search.brandId))
            .orderBy(productStats.likeCount.coalesce(0L).desc(), product.id.desc())
            .offset(search.offset)
            .limit(search.size.toLong())
            .fetch()
    }

    private fun notDeleted(): BooleanExpression = product.deletedAt.isNull

    /** 고객 목록에는 판매 중인 상품만 나온다. */
    private fun selling(): BooleanExpression = product.status.eq(ProductStatus.SELLING)

    private fun brandEq(brandId: Long?): BooleanExpression? = brandId?.let { product.brandId.eq(it) }
}
