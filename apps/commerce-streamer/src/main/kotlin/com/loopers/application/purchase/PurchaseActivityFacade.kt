package com.loopers.application.purchase

import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.purchase.PurchaseActivityCommand
import com.loopers.domain.purchase.PurchaseActivityService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PurchaseActivityFacade(
    private val purchaseActivityService: PurchaseActivityService,
    private val outboxEventRepository: OutboxEventRepository,
) {
    @Transactional
    fun record(command: PurchaseActivityCommand) {
        purchaseActivityService.record(command)
        outboxEventRepository.markSent(command.eventId)
    }

    fun conflictsWith(command: PurchaseActivityCommand): Boolean = purchaseActivityService.conflictsWith(command)

    fun markSent(eventId: String) = outboxEventRepository.markSent(eventId)
}
