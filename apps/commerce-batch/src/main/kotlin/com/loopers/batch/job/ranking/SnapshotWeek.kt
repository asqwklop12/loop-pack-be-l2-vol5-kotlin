package com.loopers.batch.job.ranking

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class SnapshotWeek(
    val monday: LocalDate,
) {
    init {
        require(monday.dayOfWeek == DayOfWeek.MONDAY) { "주는 월요일로 시작해야 합니다. [시작일 = $monday]" }
    }

    val startUtc: LocalDateTime get() = monday.atStartOfDay()

    val endUtc: LocalDateTime get() = monday.plusWeeks(1).atStartOfDay()

    val snapshotId: String get() = snapshotIdOf(FIRST_REVISION)

    val lateSnapshotId: String get() = snapshotIdOf(LATE_REVISION)

    fun snapshotIdOf(revision: Int): String {
        require(revision in FIRST_REVISION..LATE_REVISION) { "리비전은 r1 이나 r2 여야 합니다. [리비전 = $revision]" }
        return "%04d_%02d_%d_r%d".format(monday.year, monday.monthValue, (monday.dayOfMonth - 1) / 7 + 1, revision)
    }

    companion object {
        const val FIRST_REVISION = 1
        const val LATE_REVISION = 2

        fun revisionOf(snapshotId: String): Int {
            val revision = snapshotId.substringAfterLast("_r", "").toIntOrNull()
            require(revision != null && revision in FIRST_REVISION..LATE_REVISION) {
                "스냅샷 식별자의 리비전은 r1 이나 r2 여야 합니다. [스냅샷 식별자 = $snapshotId]"
            }
            return revision
        }

        fun inputSnapshotIdsOf(snapshotId: String): List<String> {
            val week = snapshotId.substringBeforeLast("_r")
            return (FIRST_REVISION..revisionOf(snapshotId)).map { "${week}_r$it" }
        }

        fun endedBefore(requestDate: LocalDate): SnapshotWeek =
            SnapshotWeek(requestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1))
    }
}
