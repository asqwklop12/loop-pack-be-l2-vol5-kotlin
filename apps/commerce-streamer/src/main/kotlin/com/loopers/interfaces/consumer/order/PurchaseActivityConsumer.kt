package com.loopers.interfaces.consumer.order

import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.purchase.PurchaseActivityCommand
import com.loopers.domain.purchase.PurchaseActivityService
import com.loopers.domain.purchase.PurchaseConflictException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
class PurchaseActivityConsumer(
    private val orderEventRecords: OrderEventRecords,
    private val purchaseActivityService: PurchaseActivityService,
) {
    @KafkaListener(
        topics = ["\${order-event.topic}"],
        groupId = GROUP,
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(records: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        orderEventRecords.confirmedOf(records)
            .map { commandOf(it) }
            .forEach { record(it) }

        acknowledgment.acknowledge()
    }

    private fun record(command: PurchaseActivityCommand) {
        try {
            purchaseActivityService.record(command)
        } catch (_: DataIntegrityViolationException) {
            throw PurchaseConflictException("이미 처리한 이벤트다. [eventId = ${command.eventId}]")
        }
    }

    private fun commandOf(event: OrderConfirmedEvent) = PurchaseActivityCommand(
        eventId = event.eventId,
        orderId = event.orderId,
        userId = event.userId,
        confirmedAt = confirmedAtOf(event.confirmedAt),
        items = event.items.map { PurchaseActivityCommand.Item(it.productId, it.quantity) },
    )

    private fun confirmedAtOf(raw: String): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault())

    companion object {
        const val GROUP = "purchase-activity"
    }
}
