package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.SnapshotWeek
import com.loopers.batch.job.ranking.WeeklySalesSnapshotJobConfig
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklySalesSnapshotJobConfig.JOB_NAME)
@Component
class WeeklySalesSnapshotTasklet(
    @param:Value("#{jobParameters['requestDate']}") private val requestDate: LocalDate,
    private val jdbcTemplate: JdbcTemplate,
) : Tasklet {
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val week = SnapshotWeek.endedBefore(requestDate)
        if (snapshotExists(week.snapshotId)) return RepeatStatus.FINISHED

        val nowUtc = LocalDateTime.now(ZoneOffset.UTC)
        val written = jdbcTemplate.update(
            INSERT_WEEKLY_SALES,
            week.snapshotId,
            week.startUtc,
            week.endUtc,
            nowUtc,
            nowUtc,
            week.startUtc,
            week.endUtc,
        )
        contribution.incrementWriteCount(written.toLong())
        return RepeatStatus.FINISHED
    }

    private fun snapshotExists(snapshotId: String): Boolean =
        jdbcTemplate.queryForList(
            "SELECT 1 FROM product_sales_snapshot WHERE snapshot_id = ? LIMIT 1",
            snapshotId,
        ).isNotEmpty()

    companion object {
        private val INSERT_WEEKLY_SALES = """
            INSERT INTO product_sales_snapshot
                (snapshot_id, period_start, period_end, product_id, sales_quantity, created_at, updated_at)
            SELECT ?, ?, ?, product_id, SUM(quantity), ?, ?
            FROM product_purchase_activity
            WHERE confirmed_at >= ? AND confirmed_at < ? AND deleted_at IS NULL
            GROUP BY product_id
        """.trimIndent()
    }
}
