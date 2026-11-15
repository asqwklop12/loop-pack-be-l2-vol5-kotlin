package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

        @DisplayName("스냅샷 식별자가 연도_두 자리 월_주차_r버전 형식이면, 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["2026_01_1_r1", "2026_12_5_r1", "2026_03_2_r10"])
        fun createsSnapshot_whenSnapshotIdMatchesFormat(snapshotId: String) {
            assertThat(snapshot(snapshotId = snapshotId).snapshotId).isEqualTo(snapshotId)
        }

        @DisplayName("스냅샷 식별자가 형식에 맞지 않으면, IllegalArgumentException 이 발생한다.")
        @ParameterizedTest
        @ValueSource(
            strings = [
                "",
                "2026_3_1_r1",
                "2026_00_1_r1",
                "2026_13_1_r1",
                "2026_11_0_r1",
                "2026_11_6_r1",
                "2026_11_1_r0",
                "2026_11_1_r01",
                "2026_11_1",
                "26_11_1_r1",
                "2026-11-1-r1",
            ],
        )
        fun throwsIllegalArgumentException_whenSnapshotIdDoesNotMatchFormat(snapshotId: String) {
            assertThrows<IllegalArgumentException> { snapshot(snapshotId = snapshotId) }
        }

        @DisplayName("구간 시작과 끝이 같으면, IllegalArgumentException 이 발생한다.")
        @Test
        fun throwsIllegalArgumentException_whenPeriodIsEmpty() {
            assertThrows<IllegalArgumentException> { snapshot(periodEnd = start) }
        }

        @DisplayName("구간 시작이 끝보다 늦으면, IllegalArgumentException 이 발생한다.")
        @Test
        fun throwsIllegalArgumentException_whenPeriodIsReversed() {
            assertThrows<IllegalArgumentException> { snapshot(periodEnd = start.minusHours(1)) }
        }

        @DisplayName("판매 수량이 0 이하면, IllegalArgumentException 이 발생한다.")
        @Test
        fun throwsIllegalArgumentException_whenSalesQuantityIsNotPositive() {
            assertThrows<IllegalArgumentException> { snapshot(salesQuantity = 0) }
        }
    }
}
