package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_like",
    uniqueConstraints = [UniqueConstraint(name = "uk_like_user_product", columnNames = ["user_id", "product_id"])],
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
