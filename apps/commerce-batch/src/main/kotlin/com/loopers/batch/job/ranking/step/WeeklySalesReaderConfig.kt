package com.loopers.batch.job.ranking.step

import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class WeeklySalesReaderConfig(
    private val dataSource: DataSource,
) {
    @StepScope
    @Bean(READER_NAME)
    fun weeklySalesReader(
        @Value("#{jobParameters['snapshotId']}") snapshotId: String,
    ): JdbcCursorItemReader<ProductSales> =
        JdbcCursorItemReaderBuilder<ProductSales>()
            .name(READER_NAME)
            .dataSource(dataSource)
            .sql(SELECT_WEEKLY_SALES)
            .queryArguments(snapshotId)
            .rowMapper { rs, _ -> ProductSales(rs.getLong("product_id"), rs.getLong("sales_quantity")) }
            .build()

    companion object {
        const val READER_NAME = "weeklySalesReader"

        private val SELECT_WEEKLY_SALES = """
            SELECT product_id, sales_quantity
            FROM product_sales_snapshot
            WHERE snapshot_id = ?
            ORDER BY sales_quantity DESC, product_id
        """.trimIndent()
    }
}
