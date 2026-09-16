package com.loopers.domain.order

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderTest {
    private fun item(productId: Long = 1L, quantity: Int = 1, unitPrice: Long = 1_000) =
        OrderItem(productId = productId, quantity = quantity, unitPrice = Money(unitPrice))

    @DisplayName("주문을 만들 때, ")
    @Nested
    inner class Create {
        @DisplayName("품목이 하나도 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenItemsAreEmpty() {
            val result = assertThrows<CoreException> { Order(userId = 1L, items = emptyList()) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("같은 상품이 두 번 들어오면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenItemsHaveDuplicateProduct() {
            val items = listOf(item(productId = 1L), item(productId = 1L))

            val result = assertThrows<CoreException> { Order(userId = 1L, items = items) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("합계는 품목 금액의 합이고, DRAFT 로 시작한다.")
        @Test
        fun sumsItemAmounts_andStartsAsDraft() {
            val items = listOf(
                item(productId = 1L, quantity = 2, unitPrice = 1_000),
                item(productId = 2L, quantity = 3, unitPrice = 500),
            )

            val order = Order(userId = 1L, items = items)

            assertAll(
                { assertThat(order.totalAmount).isEqualTo(Money(3_500)) },
                { assertThat(order.status).isEqualTo(OrderStatus.DRAFT) },
                { assertThat(order.paidAmount).isNull() },
                { assertThat(order.items).hasSize(2) },
            )
        }
    }

    @DisplayName("주문을 확정할 때, ")
    @Nested
    inner class Confirm {
        @DisplayName("결제액이 합계와 다르면, BAD_REQUEST 예외가 발생하고 DRAFT 가 유지된다.")
        @Test
        fun throwsBadRequestException_whenPaidAmountDiffersFromTotal() {
            val order = Order(userId = 1L, items = listOf(item(quantity = 2, unitPrice = 1_000)))

            val result = assertThrows<CoreException> { order.confirm(Money(1_000)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(order.status).isEqualTo(OrderStatus.DRAFT)
        }

        @DisplayName("이미 확정된 주문을 다시 확정하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenAlreadyConfirmed() {
            val order = Order(userId = 1L, items = listOf(item(quantity = 2, unitPrice = 1_000)))
            order.confirm(Money(2_000))

            val result = assertThrows<CoreException> { order.confirm(Money(2_000)) }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("확정하면, 결제액이 저장되고 CONFIRMED 가 된다.")
        @Test
        fun storesPaidAmount_andBecomesConfirmed() {
            val order = Order(userId = 1L, items = listOf(item(quantity = 2, unitPrice = 1_000)))

            order.confirm(Money(2_000))

            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED) },
                { assertThat(order.paidAmount).isEqualTo(Money(2_000)) },
                { assertThat(order.totalAmount).isEqualTo(Money(2_000)) },
            )
        }
    }

    @DisplayName("주문 소유자를 확인할 때, ")
    @Nested
    inner class OwnedBy {
        @DisplayName("소유자가 아니면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenNotOwner() {
            val order = Order(userId = 1L, items = listOf(item()))

            assertThat(order.isOwnedBy(2L)).isFalse()
        }

        @DisplayName("소유자면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenOwner() {
            val order = Order(userId = 1L, items = listOf(item()))

            assertThat(order.isOwnedBy(1L)).isTrue()
        }
    }
}
