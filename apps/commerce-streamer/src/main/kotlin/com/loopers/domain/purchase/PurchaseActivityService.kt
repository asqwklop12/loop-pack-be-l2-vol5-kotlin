package com.loopers.domain.purchase

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PurchaseActivityService(
    private val purchaseActivityRepository: PurchaseActivityRepository,
) {
    @Transactional
    fun record(commands: List<PurchaseActivityCommand>): List<ProductPurchaseActivity> {
        val activities = commands.flatMap { command ->
            command.items.map {
                ProductPurchaseActivity(
                    userId = command.userId,
                    orderId = command.orderId,
                    productId = it.productId,
                    quantity = it.quantity,
                )
            }
        }

        return purchaseActivityRepository.saveAll(activities)
    }
}
