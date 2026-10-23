package com.loopers.interfaces.scheduler

import com.loopers.application.product.ProductStatsRefresher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ProductStatsScheduler(
    private val productStatsRefresher: ProductStatsRefresher,
) {
    @Scheduled(cron = "\${product.stats.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    fun refresh() {
        productStatsRefresher.refresh()
    }
}
