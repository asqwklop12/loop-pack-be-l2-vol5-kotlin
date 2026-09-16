package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockTest {
    @DisplayName("재고를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("수량이 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenQuantityIsNegative() {
            val result = assertThrows<CoreException> { Stock(-1) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("수량이 0이면, 정상적으로 생성된다.")
        @Test
        fun createsStock_whenQuantityIsZero() {
            val stock = Stock(0)

            assertThat(stock.quantity).isEqualTo(0)
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    inner class Decrease {
        @DisplayName("차감 수량이 보유 재고보다 크면, 거절하고 기존 재고가 유지된다.")
        @Test
        fun throwsBadRequestException_whenAmountExceedsQuantity() {
            val stock = Stock(5)

            val result = assertThrows<CoreException> { stock.decrease(6) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(stock.quantity).isEqualTo(5)
        }

        @DisplayName("차감 수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenAmountIsNotPositive() {
            val stock = Stock(5)

            val result = assertThrows<CoreException> { stock.decrease(0) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("보유 재고보다 적게 차감하면, 남은 수량을 반환한다.")
        @Test
        fun returnsRemaining_whenAmountIsLessThanQuantity() {
            val stock = Stock(5)

            val result = stock.decrease(2)

            assertThat(result.quantity).isEqualTo(3)
        }

        @DisplayName("보유 재고 전량을 차감하면, 0이 된다.")
        @Test
        fun returnsZero_whenAmountEqualsQuantity() {
            val stock = Stock(5)

            val result = stock.decrease(5)

            assertThat(result.quantity).isEqualTo(0)
        }
    }

    @DisplayName("재고를 복원할 때, ")
    @Nested
    inner class Increase {
        @DisplayName("복원 수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenAmountIsNotPositive() {
            val stock = Stock(5)

            val result = assertThrows<CoreException> { stock.increase(0) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("복원하면, 늘어난 수량을 반환한다.")
        @Test
        fun returnsIncreased_whenAmountIsPositive() {
            val stock = Stock(5)

            val result = stock.increase(3)

            assertThat(result.quantity).isEqualTo(8)
        }
    }
}
