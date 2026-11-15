package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class ProductSalesSnapshotTest {
    private val start = ZonedDateTime.parse("2026-11-14T00:00:00Z")

    private fun snapshot(
        snapshotId: String = "2026_11_2_r1",
        periodStart: ZonedDateTime = start,
        periodEnd: ZonedDateTime = start.plusDays(1),
        salesQuantity: Long = 3,
    ) = ProductSalesSnapshot(
        snapshotId = snapshotId,
        periodStart = periodStart,
        periodEnd = periodEnd,
        productId = 1L,
        salesQuantity = salesQuantity,
    )

    @DisplayName("스냅샷을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("구간 시작이 끝보다 앞서면, 생성된다.")
        @Test
        fun createsSnapshot_whenPeriodIsOrdered() {
            val snapshot = snapshot()

            assertThat(snapshot.snapshotId).isEqualTo("2026_11_2_r1")
            assertThat(snapshot.periodStart).isEqualTo(start)
            assertThat(snapshot.periodEnd).isEqualTo(start.plusDays(1))
            assertThat(snapshot.salesQuantity).isEqualTo(3)
        }

        @DisplayName("스냅샷 식별자가 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenSnapshotIdIsBlank() {
            val result = assertThrows<CoreException> { snapshot(snapshotId = " ") }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("구간 시작과 끝이 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPeriodIsEmpty() {
            val result = assertThrows<CoreException> { snapshot(periodEnd = start) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("구간 시작이 끝보다 늦으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPeriodIsReversed() {
            val result = assertThrows<CoreException> { snapshot(periodEnd = start.minusHours(1)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("판매 수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenSalesQuantityIsNotPositive() {
            val result = assertThrows<CoreException> { snapshot(salesQuantity = 0) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
