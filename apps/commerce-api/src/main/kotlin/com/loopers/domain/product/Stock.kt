package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

@Embeddable
class Stock(
    val quantity: Int,
) {
    init {
        if (quantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고는 음수일 수 없습니다.")
    }

    fun decrease(amount: Int): Stock {
        requirePositive(amount)
        if (amount > quantity) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. [보유 = $quantity, 요청 = $amount]")
        }

        return Stock(quantity - amount)
    }

    fun increase(amount: Int): Stock {
        requirePositive(amount)

        return Stock(quantity + amount)
    }

    fun isEmpty(): Boolean = quantity == 0

    private fun requirePositive(amount: Int) {
        if (amount <= 0) throw CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.")
    }

    override fun equals(other: Any?): Boolean = other is Stock && other.quantity == quantity

    override fun hashCode(): Int = quantity.hashCode()

    override fun toString(): String = "$quantity"
}
