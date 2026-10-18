package com.loopers.application.product

import com.loopers.domain.product.ProductStatsLock
import com.loopers.domain.product.ProductStatsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProductStatsRefresher(
    private val productStatsRepository: ProductStatsRepository,
    private val productStatsLock: ProductStatsLock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refresh(): Int {
        if (!productStatsLock.tryAcquire()) {
            log.info("다른 인스턴스가 집계 중이라 건너뛴다.")

            return 0
        }

        return try {
            val started = System.currentTimeMillis()
            val rows = productStatsRepository.replaceAllFromLikes()
            log.info("상품 통계 집계 완료. rows={} elapsed={}ms", rows, System.currentTimeMillis() - started)

            rows
        } catch (e: Exception) {
            log.error("상품 통계 집계 실패. 다음 주기를 기다린다.", e)

            0
        } finally {
            productStatsLock.release()
        }
    }
}
