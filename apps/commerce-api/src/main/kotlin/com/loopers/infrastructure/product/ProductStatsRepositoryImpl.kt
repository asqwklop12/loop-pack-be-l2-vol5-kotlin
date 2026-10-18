package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStatsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductStatsRepositoryImpl(
    private val productStatsJpaRepository: ProductStatsJpaRepository,
) : ProductStatsRepository {
    @Transactional
    override fun replaceAllFromLikes(): Int {
        productStatsJpaRepository.deleteAllRows()

        return productStatsJpaRepository.insertAllFromLikes()
    }

    @Transactional(readOnly = true)
    override fun countsOf(productIds: List<Long>): Map<Long, Long> {
        if (productIds.isEmpty()) return emptyMap()

        return productStatsJpaRepository.findAllByProductIdIn(productIds)
            .associate { it.productId to it.likeCount }
    }
}
