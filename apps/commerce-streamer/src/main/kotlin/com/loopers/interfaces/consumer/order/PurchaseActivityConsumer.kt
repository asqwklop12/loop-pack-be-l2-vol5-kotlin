package com.loopers.interfaces.consumer.order

import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.purchase.PurchaseActivityCommand
import com.loopers.domain.purchase.PurchaseActivityService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

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
    fun consume(records: List<ConsumerRecord<String, String>>, acknowledgment: Acknowledgment) {
        val commands = orderEventRecords.confirmedOf(records).map { event ->
            PurchaseActivityCommand(
                orderId = event.orderId,
                userId = event.userId,
                items = event.items.map { PurchaseActivityCommand.Item(it.productId, it.quantity) },
            )
        }
        purchaseActivityService.record(commands)

        acknowledgment.acknowledge()
    }

    companion object {
        const val GROUP = "purchase-activity"
    }
}
