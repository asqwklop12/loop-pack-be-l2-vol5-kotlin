package com.loopers.job.ranking

import com.loopers.batch.job.ranking.step.ProductSales
import com.loopers.batch.job.ranking.step.WeeklySalesReaderConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.batch.test.MetaDataInstanceFactory
import org.springframework.batch.test.StepScopeTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
class WeeklySalesReaderTest @Autowired constructor(
    @param:Qualifier(WeeklySalesReaderConfig.READER_NAME) private val reader: JdbcCursorItemReader<ProductSales>,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("지정한 주의 스냅샷만 읽는다.")
    @Test
    fun readsOnlyGivenSnapshot() {
        snapshot("2026_11_1_r1", productId = 1L, salesQuantity = 20L)
        snapshot("2026_11_2_r1", productId = 2L, salesQuantity = 90L)
        snapshot("2026_11_3_r1", productId = 3L, salesQuantity = 70L)

        assertThat(readAll("2026_11_2_r1")).containsExactly(ProductSales(2L, 90L))
    }

    @DisplayName("판매 수량이 많은 순으로 읽고, 수량이 같으면 상품 id 가 작은 순으로 읽는다.")
    @Test
    fun readsBySalesQuantityDescThenProductIdAsc() {
        snapshot("2026_11_2_r1", productId = 3L, salesQuantity = 40L)
        snapshot("2026_11_2_r1", productId = 1L, salesQuantity = 40L)
        snapshot("2026_11_2_r1", productId = 2L, salesQuantity = 90L)
        snapshot("2026_11_2_r1", productId = 4L, salesQuantity = 5L)

        assertThat(readAll("2026_11_2_r1")).containsExactly(
            ProductSales(2L, 90L),
            ProductSales(1L, 40L),
            ProductSales(3L, 40L),
            ProductSales(4L, 5L),
        )
    }

    @DisplayName("지정한 주의 스냅샷이 없으면, 아무것도 읽지 않는다.")
    @Test
    fun readsNothing_whenSnapshotIsMissing() {
        snapshot("2026_11_1_r1", productId = 1L, salesQuantity = 20L)

        assertThat(readAll("2026_11_2_r1")).isEmpty()
    }

    private fun readAll(snapshotId: String): List<ProductSales> {
        val stepExecution = MetaDataInstanceFactory.createStepExecution(
            JobParametersBuilder().addString("snapshotId", snapshotId).toJobParameters(),
        )
        return StepScopeTestUtils.doInStepScope(stepExecution) {
            reader.open(ExecutionContext())
            try {
                generateSequence { reader.read() }.toList()
            } finally {
                reader.close()
            }
        }
    }

    private fun snapshot(snapshotId: String, productId: Long, salesQuantity: Long) {
        val now = LocalDateTime.of(2026, 11, 16, 0, 0)
        jdbcTemplate.update(
            """
            INSERT INTO product_sales_snapshot
                (snapshot_id, period_start, period_end, product_id, sales_quantity, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            snapshotId,
            now.minusWeeks(1),
            now,
            productId,
            salesQuantity,
            now,
            now,
        )
    }
}
