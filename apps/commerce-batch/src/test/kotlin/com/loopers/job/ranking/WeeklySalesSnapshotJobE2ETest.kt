package com.loopers.job.ranking

import com.loopers.batch.job.ranking.SnapshotWeek
import com.loopers.batch.job.ranking.WeeklySalesSnapshotJobConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklySalesSnapshotJobConfig.JOB_NAME}"])
class WeeklySalesSnapshotJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    private val jobRepositoryTestUtils: JobRepositoryTestUtils,
    @param:Qualifier(WeeklySalesSnapshotJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private var nextOrderId = 1L

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        jdbcTemplate.execute(CREATE_PURCHASE_ACTIVITY)
        jdbcTemplate.execute(CREATE_PRODUCT_RANKING)
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("TRUNCATE TABLE product_purchase_activity")
        jdbcTemplate.execute("TRUNCATE TABLE product_ranking")
        databaseCleanUp.truncateAllTables()
        jobRepositoryTestUtils.removeJobExecutions()
    }

    @DisplayName("직전 주에 확정된 수량을 상품별로 합쳐 스냅샷으로 남긴다.")
    @Test
    fun sumsQuantityPerProductWithinLastWeek() {
        purchase(productId = 1L, quantity = 2, confirmedAt = "2026-11-09T00:00:00")
        purchase(productId = 1L, quantity = 3, confirmedAt = "2026-11-12T10:00:00")
        purchase(productId = 2L, quantity = 1, confirmedAt = "2026-11-15T23:59:59.999999")

        val exitStatus = launch("2026-11-16")

        assertThat(exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        assertThat(snapshotRows()).containsExactlyInAnyOrder(
            SnapshotRow("2026_11_2_r1", 1L, 5L, "2026-11-09T00:00", "2026-11-16T00:00"),
            SnapshotRow("2026_11_2_r1", 2L, 1L, "2026-11-09T00:00", "2026-11-16T00:00"),
        )
    }

    @DisplayName("구간 밖에서 확정된 수량은 넣지 않는다.")
    @Test
    fun excludesPurchasesOutsideWeek() {
        purchase(productId = 1L, quantity = 4, confirmedAt = "2026-11-08T23:59:59.999999")
        purchase(productId = 1L, quantity = 7, confirmedAt = "2026-11-16T00:00:00")
        purchase(productId = 2L, quantity = 1, confirmedAt = "2026-11-10T00:00:00")

        launch("2026-11-16")

        assertThat(snapshotRows().map { it.productId to it.salesQuantity }).containsExactly(2L to 1L)
    }

    @DisplayName("같은 주의 스냅샷이 이미 있으면, 다시 실행해도 그대로 둔다.")
    @Test
    fun keepsExistingSnapshot_whenWeekAlreadySnapshotted() {
        purchase(productId = 1L, quantity = 2, confirmedAt = "2026-11-10T00:00:00")
        launch("2026-11-16")
        purchase(productId = 1L, quantity = 9, confirmedAt = "2026-11-11T00:00:00")

        val exitStatus = launch("2026-11-18")

        assertThat(exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        assertThat(snapshotRows().map { it.productId to it.salesQuantity }).containsExactly(1L to 2L)
    }

    @DisplayName("직전 주에 확정된 구매가 없으면, 행 없이 성공한다.")
    @Test
    fun completesWithoutRows_whenNoPurchaseInWeek() {
        val exitStatus = launch("2026-11-16")

        assertThat(exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        assertThat(snapshotRows()).isEmpty()
    }

    @DisplayName("requestDate 가 없으면, 실패한다.")
    @Test
    fun fails_whenRequestDateIsMissing() {
        val execution = jobLauncherTestUtils.launchJob()

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode)
    }

    @DisplayName("직전 주 스냅샷으로 판매 수량 순위를 매겨 랭킹으로 남긴다.")
    @Test
    fun writesRankingFromLastWeekSnapshot() {
        purchase(productId = 1L, quantity = 3, confirmedAt = "2026-11-10T00:00:00")
        purchase(productId = 2L, quantity = 9, confirmedAt = "2026-11-11T00:00:00")
        purchase(productId = 3L, quantity = 3, confirmedAt = "2026-11-12T00:00:00")
        purchase(productId = 4L, quantity = 1, confirmedAt = "2026-11-13T00:00:00")

        val exitStatus = launch("2026-11-16")

        assertThat(exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        assertThat(rankingRows()).containsExactly(
            RankingRow("2026_11_2", "2026-11-09", 2L, 9L, 1),
            RankingRow("2026_11_2", "2026-11-09", 1L, 3L, 2),
            RankingRow("2026_11_2", "2026-11-09", 3L, 3L, 2),
            RankingRow("2026_11_2", "2026-11-09", 4L, 1L, 3),
        )
    }

    @DisplayName("같은 주의 랭킹이 이미 있으면, 랭킹을 다시 쓰지 않는다.")
    @Test
    fun skipsRanking_whenWeekAlreadyRanked() {
        purchase(productId = 1L, quantity = 3, confirmedAt = "2026-11-10T00:00:00")
        jdbcTemplate.update(
            """
            INSERT INTO product_ranking
                (snapshot_id, period_start, product_id, score, ranking, created_at, updated_at)
            VALUES ('2026_11_2', '2026-11-09', 99, 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """.trimIndent(),
        )

        val exitStatus = launch("2026-11-16")

        assertThat(exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        assertThat(rankingRows().map { it.productId }).containsExactly(99L)
    }

    private fun launch(requestDate: String): ExitStatus {
        val date = LocalDate.parse(requestDate)
        return jobLauncherTestUtils.launchJob(
            JobParametersBuilder()
                .addLocalDate("requestDate", date)
                .addString("snapshotId", SnapshotWeek.endedBefore(date).snapshotId)
                .toJobParameters(),
        ).exitStatus
    }

    private fun rankingRows(): List<RankingRow> =
        jdbcTemplate.query(
            "SELECT snapshot_id, period_start, product_id, score, ranking FROM product_ranking ORDER BY ranking, product_id",
        ) { rs, _ ->
            RankingRow(
                snapshotId = rs.getString("snapshot_id"),
                periodStart = rs.getObject("period_start", LocalDate::class.java).toString(),
                productId = rs.getLong("product_id"),
                score = rs.getLong("score"),
                ranking = rs.getInt("ranking"),
            )
        }

    private data class RankingRow(
        val snapshotId: String,
        val periodStart: String,
        val productId: Long,
        val score: Long,
        val ranking: Int,
    )

    private fun purchase(productId: Long, quantity: Int, confirmedAt: String) {
        val at = LocalDateTime.parse(confirmedAt)
        jdbcTemplate.update(
            """
            INSERT INTO product_purchase_activity
                (created_at, updated_at, order_id, product_id, quantity, user_id, confirmed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            at,
            at,
            nextOrderId++,
            productId,
            quantity,
            1L,
            at,
        )
    }

    private fun snapshotRows(): List<SnapshotRow> =
        jdbcTemplate.query(
            "SELECT snapshot_id, product_id, sales_quantity, period_start, period_end FROM product_sales_snapshot",
        ) { rs, _ ->
            SnapshotRow(
                snapshotId = rs.getString("snapshot_id"),
                productId = rs.getLong("product_id"),
                salesQuantity = rs.getLong("sales_quantity"),
                periodStart = rs.getObject("period_start", LocalDateTime::class.java).toString(),
                periodEnd = rs.getObject("period_end", LocalDateTime::class.java).toString(),
            )
        }

    private data class SnapshotRow(
        val snapshotId: String,
        val productId: Long,
        val salesQuantity: Long,
        val periodStart: String,
        val periodEnd: String,
    )

    companion object {
        private val CREATE_PURCHASE_ACTIVITY = """
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
        """.trimIndent()

        private val CREATE_PRODUCT_RANKING = """
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
        """.trimIndent()
    }
}
