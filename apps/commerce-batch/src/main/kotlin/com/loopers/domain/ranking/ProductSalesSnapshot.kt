package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "product_sales_snapshot",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_sales_snapshot_snapshot_product",
            columnNames = ["snapshot_id", "product_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_product_sales_snapshot_snapshot_quantity",
            columnList = "snapshot_id, sales_quantity desc, product_id",
        ),
    ],
)
class ProductSalesSnapshot(
    snapshotId: String,
    periodStart: ZonedDateTime,
    periodEnd: ZonedDateTime,
    productId: Long,
    salesQuantity: Long,
) : BaseEntity() {
    @Column(name = "snapshot_id", nullable = false, updatable = false, length = 30)
    var snapshotId: String = snapshotId
        protected set

    @Column(name = "period_start", nullable = false, updatable = false)
    var periodStart: ZonedDateTime = periodStart
        protected set

    @Column(name = "period_end", nullable = false, updatable = false)
    var periodEnd: ZonedDateTime = periodEnd
        protected set

    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: Long = productId
        protected set

    @Column(name = "sales_quantity", nullable = false, updatable = false)
    var salesQuantity: Long = salesQuantity
        protected set

    init {
        require(SNAPSHOT_ID_FORMAT.matches(snapshotId)) { "스냅샷 식별자 형식이 아닙니다. [스냅샷 식별자 = $snapshotId]" }
        require(periodStart.isBefore(periodEnd)) { "구간 시작이 끝보다 앞서야 합니다. [시작 = $periodStart, 끝 = $periodEnd]" }
        require(salesQuantity > 0) { "판매 수량은 1 이상이어야 합니다. [판매 수량 = $salesQuantity]" }
    }

    companion object {
        private val SNAPSHOT_ID_FORMAT = Regex("""\d{4}_(0[1-9]|1[0-2])_[1-5]_r[1-9]\d*""")
    }
}
