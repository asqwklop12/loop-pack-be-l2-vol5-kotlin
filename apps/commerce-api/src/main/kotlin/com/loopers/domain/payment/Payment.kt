package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "idx_payment_order", columnList = "order_id"),
    ],
)
class Payment(
    orderId: Long,
    amount: Money,
    status: PaymentStatus = PaymentStatus.PENDING,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: Long = orderId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = status
        protected set

    @Embedded
    @AttributeOverride(column = Column(name = "amount", nullable = false), name = "amount")
    var amount: Money = amount
        protected set

    fun markSuccess() {
        status = PaymentStatus.SUCCESS
    }

    fun markFailed() {
        status = PaymentStatus.FAILED
    }
}
