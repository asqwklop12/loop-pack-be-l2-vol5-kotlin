package com.loopers.domain.shared

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MoneyTest {
    @DisplayName("금액을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("금액이 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenAmountIsNegative() {
            val result = assertThrows<CoreException> { Money(-1) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("금액이 0원이면, 정상적으로 생성된다.")
        @Test
        fun createsMoney_whenAmountIsZero() {
            val money = Money(0)

            assertThat(money.amount).isEqualTo(0L)
        }
    }

    @DisplayName("금액을 뺄 때, ")
    @Nested
    inner class Minus {
        @DisplayName("차감액이 원금보다 크면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenSubtrahendIsGreater() {
            val money = Money(1_000)

            val result = assertThrows<CoreException> { money.minus(Money(1_001)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("차감액이 원금과 같으면, 0원이 된다.")
        @Test
        fun returnsZero_whenSubtrahendEqualsAmount() {
            val money = Money(1_000)

            val result = money.minus(Money(1_000))

            assertThat(result).isEqualTo(Money.ZERO)
        }

        @DisplayName("기존 금액은 바뀌지 않고 새 금액을 반환한다.")
        @Test
        fun doesNotMutateOriginal() {
            val money = Money(1_000)

            val result = money.minus(Money(300))

            assertThat(money.amount).isEqualTo(1_000L)
            assertThat(result.amount).isEqualTo(700L)
        }
    }

    @DisplayName("금액을 더할 때, ")
    @Nested
    inner class Plus {
        @DisplayName("합계가 표현 범위를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenSumOverflows() {
            val money = Money(Long.MAX_VALUE)

            val result = assertThrows<CoreException> { money.plus(Money(1)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
