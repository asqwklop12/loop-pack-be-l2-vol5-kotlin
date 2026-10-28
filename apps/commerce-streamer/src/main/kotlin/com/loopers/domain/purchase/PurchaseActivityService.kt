package com.loopers.domain.purchase

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PurchaseActivityService(
    private val processedConfirmationRepository: ProcessedConfirmationRepository,
    private val purchaseActivityRepository: PurchaseActivityRepository,
) {
    @Transactional
    fun record(command: PurchaseActivityCommand) {
        processedConfirmationRepository.save(
            ProcessedOrderConfirmation(eventId = command.eventId, orderId = command.orderId),
        )

        purchaseActivityRepository.saveAll(
            command.items.map {
                ProductPurchaseActivity(
                    userId = command.userId,
                    orderId = command.orderId,
                    productId = it.productId,
                    quantity = it.quantity,
                    confirmedAt = command.confirmedAt,
                )
            },
        )
    }
}
