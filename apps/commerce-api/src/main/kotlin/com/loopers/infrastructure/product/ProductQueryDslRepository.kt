package com.loopers.infrastructure.product

import com.loopers.domain.like.QLike
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSearch
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.product.QProduct
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

@Component
class ProductQueryDslRepository(
    private val queryFactory: JPAQueryFactory,
) {
    private val product = QProduct.product
    private val like = QLike.like

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
     * 좋아요 수는 관계에서 센다. 관계가 없는 상품도 나와야 하므로 left join 이다.
     */
    private fun searchByLikes(search: ProductSearch): List<Product> {
        return queryFactory
            .selectFrom(product)
            .leftJoin(like).on(like.productId.eq(product.id))
            .where(notDeleted(), selling(), brandEq(search.brandId))
            .groupBy(product.id)
            .orderBy(like.count().desc(), product.id.desc())
            .offset(search.offset)
            .limit(search.size.toLong())
            .fetch()
    }

    private fun notDeleted(): BooleanExpression = product.deletedAt.isNull

    /** 고객 목록에는 판매 중인 상품만 나온다. */
    private fun selling(): BooleanExpression = product.status.eq(ProductStatus.SELLING)

    private fun brandEq(brandId: Long?): BooleanExpression? = brandId?.let { product.brandId.eq(it) }
}
