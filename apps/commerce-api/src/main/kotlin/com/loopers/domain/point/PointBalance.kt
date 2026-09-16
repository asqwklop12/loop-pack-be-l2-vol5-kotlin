package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "point_balance")
class PointBalance(
    userId: Long,
    balance: Money = Money.ZERO,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    var userId: Long = userId
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "balance", nullable = false))
    var balance: Money = balance
        protected set

    /**
     * 잔액은 0원도 유효하지만 충전액은 양수여야 한다. 0원은 충전이 아니다.
     */
    fun charge(amount: Money) {
        if (amount.amount <= 0) throw CoreException(ErrorType.BAD_REQUEST, "충전액은 1원 이상이어야 합니다.")

        this.balance = balance.plus(amount)
    }
}
