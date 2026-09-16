package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_item")
class OrderItem(
    productId: Long,
    quantity: Int,
    unitPrice: Money,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    var order: Order? = null
        protected set

    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: Long = productId
        protected set

    @Column(name = "quantity", nullable = false, updatable = false)
    var quantity: Int = quantity
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "unit_price", nullable = false, updatable = false))
    var unitPrice: Money = unitPrice
        protected set

    init {
        if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
    }

    /** 품목 금액. 단가 × 수량 */
    val amount: Money
        get() = unitPrice.times(quantity)

    internal fun belongTo(order: Order) {
        this.order = order
    }
}
