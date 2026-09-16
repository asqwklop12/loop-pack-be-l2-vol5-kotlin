package com.loopers.domain.point

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PointBalanceTest {
    @DisplayName("포인트 잔액을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("잔액을 주지 않으면, 0원으로 시작한다.")
        @Test
        fun startsAtZero_whenBalanceIsNotGiven() {
            val point = PointBalance(userId = 1L)

            assertThat(point.balance).isEqualTo(Money.ZERO)
        }
    }

    @DisplayName("포인트를 충전할 때, ")
    @Nested
    inner class Charge {
        @DisplayName("충전액이 0원이면, BAD_REQUEST 예외가 발생하고 기존 잔액이 유지된다.")
        @Test
        fun throwsBadRequestException_whenAmountIsZero() {
            val point = PointBalance(userId = 1L, balance = Money(1_000))

            val result = assertThrows<CoreException> { point.charge(Money.ZERO) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(point.balance).isEqualTo(Money(1_000))
        }

        @DisplayName("충전하면, 기존 잔액에 더해진다.")
        @Test
        fun addsToBalance_whenAmountIsPositive() {
            val point = PointBalance(userId = 1L, balance = Money(1_000))

            point.charge(Money(9_000))

            assertThat(point.balance).isEqualTo(Money(10_000))
        }

        @DisplayName("잔액이 0원이어도, 충전할 수 있다.")
        @Test
        fun chargesFromZeroBalance() {
            val point = PointBalance(userId = 1L)

            point.charge(Money(10_000))

            assertThat(point.balance).isEqualTo(Money(10_000))
        }

        @DisplayName("충전 후 잔액이 표현 범위를 넘으면, BAD_REQUEST 예외가 발생하고 기존 잔액이 유지된다.")
        @Test
        fun throwsBadRequestException_whenSumOverflows() {
            val point = PointBalance(userId = 1L, balance = Money(Long.MAX_VALUE))

            val result = assertThrows<CoreException> { point.charge(Money(1)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(point.balance).isEqualTo(Money(Long.MAX_VALUE))
        }
    }
}
