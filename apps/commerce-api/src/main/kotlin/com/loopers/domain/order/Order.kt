package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "orders")
class Order(
    userId: Long,
    items: List<OrderItem>,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = userId
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _items: MutableList<OrderItem> = items.toMutableList()

    val items: List<OrderItem> get() = _items.toList()

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.DRAFT
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = items.fold(Money.ZERO) { acc, item -> acc.plus(item.amount) }
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "paid_amount"))
    var paidAmount: Money? = null
        protected set

    init {
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 품목이 하나도 없습니다.")
        if (items.distinctBy { it.productId }.size != items.size) {
            throw CoreException(ErrorType.BAD_REQUEST, "같은 상품이 여러 품목으로 들어왔습니다.")
        }

        _items.forEach { it.belongTo(this) }
    }

    fun isOwnedBy(userId: Long): Boolean = this.userId == userId

    /**
     * 확정은 금액이 고정되는 사건이다. 결제액은 합계와 같아야 한다.
     */
    fun confirm(paidAmount: Money) {
        if (status != OrderStatus.DRAFT) {
            throw CoreException(ErrorType.CONFLICT, "확정할 수 없는 주문 상태입니다. [상태 = $status]")
        }
        if (paidAmount != totalAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제액이 주문 합계와 다릅니다. [합계 = $totalAmount, 결제액 = $paidAmount]")
        }

        this.paidAmount = paidAmount
        this.status = OrderStatus.CONFIRMED
    }
}
