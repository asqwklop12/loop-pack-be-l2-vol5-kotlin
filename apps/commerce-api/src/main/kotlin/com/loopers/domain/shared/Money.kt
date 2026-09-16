package com.loopers.domain.shared

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

@Embeddable
class Money(
    val amount: Long,
) {
    init {
        if (amount < 0) throw CoreException(ErrorType.BAD_REQUEST, "금액은 음수일 수 없습니다.")
    }

    fun plus(other: Money): Money {
        val sum = runCatching { Math.addExact(amount, other.amount) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "금액의 표현 범위를 넘었습니다.") }

        return Money(sum)
    }

    fun minus(other: Money): Money {
        if (other.amount > amount) throw CoreException(ErrorType.BAD_REQUEST, "차감액이 원금을 넘습니다.")

        return Money(amount - other.amount)
    }

    override fun equals(other: Any?): Boolean = other is Money && other.amount == amount

    override fun hashCode(): Int = amount.hashCode()

    override fun toString(): String = "$amount"

    companion object {
        val ZERO = Money(0)
    }
}
