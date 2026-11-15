package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
        if (snapshotId.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "스냅샷 식별자가 비어 있습니다.")
        }
        if (!periodStart.isBefore(periodEnd)) {
            throw CoreException(ErrorType.BAD_REQUEST, "구간 시작이 끝보다 앞서야 합니다. [시작 = $periodStart, 끝 = $periodEnd]")
        }
        if (salesQuantity <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "판매 수량은 1 이상이어야 합니다. [판매 수량 = $salesQuantity]")
        }
    }
}
