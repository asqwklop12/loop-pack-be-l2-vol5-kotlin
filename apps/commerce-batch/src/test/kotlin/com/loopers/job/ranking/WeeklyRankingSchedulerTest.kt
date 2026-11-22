package com.loopers.job.ranking

import com.loopers.batch.job.ranking.WeeklyRankingScheduler
import com.loopers.batch.job.ranking.WeeklySalesSnapshotJobConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.support.CronExpression
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@SpringBootTest(
    properties = [
        "spring.batch.job.name=${WeeklySalesSnapshotJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
        "${WeeklyRankingScheduler.ENABLED_PROPERTY}=true",
    ],
)
@SpringBatchTest
class WeeklyRankingSchedulerTest @Autowired constructor(
    private val scheduler: WeeklyRankingScheduler,
    private val jobRepositoryTestUtils: JobRepositoryTestUtils,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS product_purchase_activity (
                id bigint NOT NULL AUTO_INCREMENT,
                created_at datetime(6) NOT NULL,
                deleted_at datetime(6) DEFAULT NULL,
                updated_at datetime(6) NOT NULL,
                order_id bigint NOT NULL,
                product_id bigint NOT NULL,
                quantity int NOT NULL,
                user_id bigint NOT NULL,
                confirmed_at datetime(6) NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_purchase_activity_order_product (order_id, product_id)
            )
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS product_ranking (
                id bigint NOT NULL AUTO_INCREMENT,
                created_at datetime(6) NOT NULL,
                deleted_at datetime(6) DEFAULT NULL,
                updated_at datetime(6) NOT NULL,
                snapshot_id varchar(30) NOT NULL,
                period_start date NOT NULL,
                product_id bigint NOT NULL,
                score bigint NOT NULL,
                ranking int NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_product_ranking_snapshot_product (snapshot_id, product_id)
            )
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS product_ranking_pointer (
                id bigint NOT NULL AUTO_INCREMENT,
                created_at datetime(6) NOT NULL,
                deleted_at datetime(6) DEFAULT NULL,
                updated_at datetime(6) NOT NULL,
                snapshot_id varchar(30) NOT NULL,
                status varchar(20) NOT NULL,
                product_count bigint DEFAULT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_product_ranking_pointer_snapshot (snapshot_id)
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("TRUNCATE TABLE product_purchase_activity")
        jdbcTemplate.execute("TRUNCATE TABLE product_ranking")
        jdbcTemplate.execute("TRUNCATE TABLE product_ranking_pointer")
        databaseCleanUp.truncateAllTables()
        jobRepositoryTestUtils.removeJobExecutions()
    }

    @DisplayName("r1 의 다음 실행은 UTC 월요일 00:30 이다.")
    @ParameterizedTest
    @CsvSource(
        "2026-11-15T23:59:59Z, 2026-11-16T00:30Z",
        "2026-11-16T00:29:59Z, 2026-11-16T00:30Z",
        "2026-11-16T00:30:00Z, 2026-11-23T00:30Z",
        "2026-11-18T12:00:00Z, 2026-11-23T00:30Z",
    )
    fun nextFirstRunIsMonday0030Utc(now: String, expected: String) {
        val next = CronExpression.parse(WeeklyRankingScheduler.FIRST_CRON)
            .next(ZonedDateTime.parse(now).withZoneSameInstant(ZoneOffset.UTC))

        assertThat(next).isEqualTo(ZonedDateTime.parse(expected))
    }

    @DisplayName("r2 의 다음 실행은 UTC 월요일 01:30 이다.")
    @ParameterizedTest
    @CsvSource(
        "2026-11-16T00:30:00Z, 2026-11-16T01:30Z",
        "2026-11-16T01:29:59Z, 2026-11-16T01:30Z",
        "2026-11-16T01:30:00Z, 2026-11-23T01:30Z",
        "2026-11-18T12:00:00Z, 2026-11-23T01:30Z",
    )
    fun nextLateRunIsMonday0130Utc(now: String, expected: String) {
        val next = CronExpression.parse(WeeklyRankingScheduler.LATE_CRON)
            .next(ZonedDateTime.parse(now).withZoneSameInstant(ZoneOffset.UTC))

        assertThat(next).isEqualTo(ZonedDateTime.parse(expected))
    }

    @DisplayName("날짜와 리비전으로 requestDate 와 snapshotId 를 함께 정해, 직전 주의 그 리비전 랭킹을 남긴다.")
    @Test
    fun launchesJobWithMatchingParameters() {
        purchase(orderId = 1L, productId = 10L, quantity = 4, confirmedAt = "2026-11-10T00:00:00")
        purchase(orderId = 2L, productId = 20L, quantity = 2, confirmedAt = "2026-11-12T00:00:00")
        val first = scheduler.launch(LocalDate.parse("2026-11-16"), 1)
        purchase(orderId = 3L, productId = 20L, quantity = 5, confirmedAt = "2026-11-12T00:00:00", createdAt = "2026-11-16T00:10:00")

        val late = scheduler.launch(LocalDate.parse("2026-11-16"), 2)

        assertThat(first.status).isEqualTo(BatchStatus.COMPLETED)
        assertThat(first.jobParameters.getLocalDate("requestDate")).isEqualTo(LocalDate.parse("2026-11-16"))
        assertThat(first.jobParameters.getString("snapshotId")).isEqualTo("2026_11_2_r1")
        assertThat(late.status).isEqualTo(BatchStatus.COMPLETED)
        assertThat(late.jobParameters.getString("snapshotId")).isEqualTo("2026_11_2_r2")
        assertThat(rankedProductsOf("2026_11_2_r1")).containsExactly(10L, 20L)
        assertThat(rankedProductsOf("2026_11_2_r2")).containsExactly(20L, 10L)
    }

    private fun rankedProductsOf(snapshotId: String): List<Long> =
        jdbcTemplate.queryForList(
            "SELECT product_id FROM product_ranking WHERE snapshot_id = ? ORDER BY ranking",
            Long::class.java,
            snapshotId,
        )

    private fun purchase(orderId: Long, productId: Long, quantity: Int, confirmedAt: String, createdAt: String = confirmedAt) {
        val at = LocalDateTime.parse(confirmedAt)
        val created = LocalDateTime.parse(createdAt)
        jdbcTemplate.update(
            """
            INSERT INTO product_purchase_activity
                (created_at, updated_at, order_id, product_id, quantity, user_id, confirmed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            created,
            created,
            orderId,
            productId,
            quantity,
            1L,
            at,
        )
    }
}
