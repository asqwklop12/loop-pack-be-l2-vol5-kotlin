package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "coupon",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_coupon_campaign_id_user_id", columnNames = ["campaign_id", "user_id"]),
    ],
)
class Coupon(
    campaignId: Long,
    userId: Long,
) : BaseEntity() {
    @Column(name = "campaign_id", nullable = false, updatable = false)
    var campaignId: Long = campaignId
        protected set

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = userId
        protected set
}
