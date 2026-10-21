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
     * `product` 에서 조인해 내려가면 정렬 키가 `product_stats` 에 있어 정렬을 인덱스로 건너뛸 수 없다.
     * 그래서 `product_stats` 에서 먼저 id 만 뽑는다 — `idx_product_stats_like_count` 가
     * `(like_count desc, product_id desc)` 라 이 구간은 인덱스만 읽고 끝난다.
     *
     * 2단계에서 판매·삭제·브랜드 조건을 걸기 때문에 **한 페이지가 size 보다 적게 올 수 있다.**
     * 좋아요 집계에 행이 없는 상품은 1단계에서 아예 안 뽑힌다.
     */
    private fun searchByLikes(search: ProductSearch): List<Product> {
        val ids = queryFactory
            .select(productStats.productId)
            .from(productStats)
            .orderBy(productStats.likeCount.desc(), productStats.productId.desc())
            .offset(search.offset)
            .limit(search.size.toLong())
            .fetch()

        if (ids.isEmpty()) return emptyList()

        val found = queryFactory
            .selectFrom(product)
            .where(product.id.`in`(ids), notDeleted(), selling(), brandEq(search.brandId))
            .fetch()
            .associateBy { it.id }

        return ids.mapNotNull { found[it] }
    }

    private fun notDeleted(): BooleanExpression = product.deletedAt.isNull

    /** 고객 목록에는 판매 중인 상품만 나온다. */
    private fun selling(): BooleanExpression = product.status.eq(ProductStatus.SELLING)

    private fun brandEq(brandId: Long?): BooleanExpression? = brandId?.let { product.brandId.eq(it) }
}
