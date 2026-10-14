package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 좋아요 수는 상품 기준으로 센다. `product_id` 단독 인덱스가 그 조회를 받친다.
 */
@Entity
@Table(
    name = "product_like",
    indexes = [
        Index(name = "idx_like_product", columnList = "product_id"),
    ],
)
class Like(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = userId
        protected set

    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: Long = productId
        protected set
}
