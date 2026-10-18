package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_stats",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_product_stats_product", columnNames = ["product_id"]),
    ],
    indexes = [
        Index(name = "idx_product_stats_like_count", columnList = "like_count desc, product_id desc"),
    ],
)
class ProductStats(
    productId: Long,
    likeCount: Long = 0,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: Long = productId
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = likeCount
        protected set
}
