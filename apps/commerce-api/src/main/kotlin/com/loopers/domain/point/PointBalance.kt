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
import jakarta.persistence.Version

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
     * 한 사용자가 여러 주문을 동시에 결제하면 둘 다 같은 잔액을 읽는다. 그대로 두면
     * **잔액보다 많이 결제된다.** 읽은 값이 아직 유효한지를 저장할 때 확인한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    /**
     * 잔액은 0원도 유효하지만 충전액은 양수여야 한다. 0원은 충전이 아니다.
     */
    fun charge(amount: Money) {
        requirePositive(amount, "충전액")

        this.balance = balance.plus(amount)
    }

    /**
     * 잔액과 결제액의 관계는 [Money.minus] 가 지킨다. 잔액보다 큰 금액은 차감되지 않는다.
     */
    fun pay(amount: Money) {
        requirePositive(amount, "결제액")

        this.balance = balance.minus(amount)
    }

    private fun requirePositive(amount: Money, label: String) {
        if (amount.amount <= 0) throw CoreException(ErrorType.BAD_REQUEST, "${label}은 1원 이상이어야 합니다.")
    }
}
