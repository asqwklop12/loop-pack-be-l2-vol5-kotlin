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
    lines: List<OrderLine>,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = userId
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _lines: MutableList<OrderLine> = lines.toMutableList()

    val lines: List<OrderLine> get() = _lines.toList()

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.DRAFT
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = lines.fold(Money.ZERO) { acc, line -> acc.plus(line.amount) }
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "paid_amount"))
    var paidAmount: Money? = null
        protected set

    init {
        if (lines.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 line이 하나도 없습니다.")
        if (lines.distinctBy { it.productId }.size != lines.size) {
            throw CoreException(ErrorType.BAD_REQUEST, "같은 상품이 여러 품목으로 들어왔습니다.")
        }

        _lines.forEach { it.belongTo(this) }
    }

    fun isOwnedBy(userId: Long): Boolean = this.userId == userId

    fun isConfirmed(): Boolean = status == OrderStatus.CONFIRMED

    /**
     * 주문 기록은 남기고 상태만 바꾼다. 확정된 금액은 그대로 둔다.
     */
    fun cancel() {
        if (status == OrderStatus.CANCELED) {
            throw CoreException(ErrorType.CONFLICT, "이미 취소된 주문입니다.")
        }

        this.status = OrderStatus.CANCELED
    }

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
