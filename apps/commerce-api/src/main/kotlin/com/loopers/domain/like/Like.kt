package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 인덱스가 둘인 이유는 **읽는 방향이 둘**이기 때문이다. 하나가 다른 하나를 덮지 못한다.
 *
 * `uk_like_user_product` 는 한 사용자가 같은 상품을 두 번 누르지 못하게 막는다.
 * 애플리케이션도 존재 여부를 먼저 보지만, 동시 요청이 겹치면 둘 다 "없음" 을 보고
 * 둘 다 저장할 수 있다. 그 틈은 DB 제약으로만 닫힌다.
 *
 * `idx_like_product` 는 좋아요 수 집계를 받친다. 유니크 키의 선두가 `user_id` 라
 * `product_id` 로는 탐색에 쓸 수 없어 따로 필요하다.
 */
@Entity
@Table(
    name = "product_like",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_like_user_product", columnNames = ["user_id", "product_id"]),
    ],
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
