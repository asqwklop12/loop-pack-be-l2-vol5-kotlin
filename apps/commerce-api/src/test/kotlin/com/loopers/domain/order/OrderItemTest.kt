package com.loopers.domain.order

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderItemTest {
    @DisplayName("주문 품목을 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenQuantityIsNotPositive() {
            val result = assertThrows<CoreException> {
                OrderItem(productId = 1L, quantity = 0, unitPrice = Money(1_000))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("품목 금액은 단가에 수량을 곱한 값이다.")
        @Test
        fun calculatesAmount_fromUnitPriceAndQuantity() {
            val item = OrderItem(productId = 1L, quantity = 3, unitPrice = Money(1_000))

            assertThat(item.amount).isEqualTo(Money(3_000))
        }
    }
}
