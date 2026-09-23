package com.loopers.domain.point

import com.loopers.domain.shared.Money
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PointServiceIntegrationTest @Autowired constructor(
    private val pointService: PointService,
    private val pointJpaRepository: PointJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("포인트를 충전할 때, ")
    @Nested
    inner class Charge {
        @DisplayName("처음 충전하면, 잔액이 저장되고 충전 후 잔액을 반환한다.")
        @Test
        fun savesBalance_whenFirstCharge() {
            val balance = pointService.charge(userId = userId, amount = Money(10_000))

            assertThat(balance).isEqualTo(Money(10_000))
            assertThat(pointJpaRepository.findByUserId(userId)?.balance).isEqualTo(Money(10_000))
        }

        @DisplayName("두 번 충전하면, 기존 잔액에 더해진다.")
        @Test
        fun addsToExistingBalance_whenChargedAgain() {
            pointService.charge(userId = userId, amount = Money(10_000))

            val balance = pointService.charge(userId = userId, amount = Money(5_000))

            assertThat(balance).isEqualTo(Money(15_000))
            assertThat(pointJpaRepository.findByUserId(userId)?.balance).isEqualTo(Money(15_000))
        }

        @DisplayName("충전액이 0원이면, 기존 잔액이 유지된다.")
        @Test
        fun keepsBalance_whenAmountIsZero() {
            pointService.charge(userId = userId, amount = Money(10_000))

            val result = assertThrows<CoreException> { pointService.charge(userId = userId, amount = Money.ZERO) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(pointJpaRepository.findByUserId(userId)?.balance).isEqualTo(Money(10_000))
        }
    }

    @DisplayName("잔액을 조회할 때, ")
    @Nested
    inner class GetBalance {
        @DisplayName("충전한 적이 없으면, 0원을 반환한다.")
        @Test
        fun returnsZero_whenNeverCharged() {
            assertThat(pointService.getBalance(userId)).isEqualTo(Money.ZERO)
        }

        @DisplayName("충전한 적이 있으면, 저장된 잔액을 반환한다.")
        @Test
        fun returnsStoredBalance_whenCharged() {
            pointService.charge(userId = userId, amount = Money(10_000))

            assertThat(pointService.getBalance(userId)).isEqualTo(Money(10_000))
        }
    }
}
