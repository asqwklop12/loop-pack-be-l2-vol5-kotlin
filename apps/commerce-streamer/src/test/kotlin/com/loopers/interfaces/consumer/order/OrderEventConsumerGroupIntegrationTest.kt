package com.loopers.interfaces.consumer.order

import com.ninjasquad.springmockk.SpykBean
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment

@SpringBootTest
class OrderEventConsumerGroupIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val orderEventRecords: OrderEventRecords,
    private val registry: KafkaListenerEndpointRegistry,
    @Value("\${order-event.topic}") private val topic: String,
) {
    @SpykBean
    private lateinit var purchaseActivityConsumer: PurchaseActivityConsumer

    @SpykBean
    private lateinit var stockTrackingConsumer: StockTrackingConsumer

    @BeforeEach
    fun awaitAssignment() {
        val deadline = System.nanoTime() + ASSIGNMENT_TIMEOUT_NANOS
        val groups = setOf(PurchaseActivityConsumer.GROUP, StockTrackingConsumer.GROUP)

        while (System.nanoTime() < deadline) {
            val assigned = registry.listenerContainers
                .filter { !it.assignedPartitions.isNullOrEmpty() }
                .mapNotNull { it.groupId }
                .filter { it in groups }
                .toSet()

            if (assigned == groups) return

            Thread.sleep(POLL_MS)
        }

        throw IllegalStateException("컨슈머 그룹이 파티션을 배정받지 못했다.")
    }

    @DisplayName("한 토픽의 같은 메시지를 두 컨슈머 그룹이 각자 받는다.")
    @Test
    fun bothGroupsReceiveSameRecord() {
        val orderId = System.currentTimeMillis()
        val payload = OrderConfirmedEvent(
            orderId = orderId,
            items = listOf(OrderConfirmedEvent.Item(productId = 7, quantity = 2)),
            eventType = "OrderConfirmed",
        )
        val purchased = slot<List<ConsumerRecord<String, String>>>()
        val tracked = slot<List<ConsumerRecord<String, String>>>()

        kafkaTemplate.send(topic, orderId.toString(), payload).get()

        verify(timeout = DELIVERY_TIMEOUT_MS) {
            purchaseActivityConsumer.consume(capture(purchased), any<Acknowledgment>())
            stockTrackingConsumer.consume(capture(tracked), any<Acknowledgment>())
        }
        val delivered = orderEventRecords.confirmedOf(purchased.captured).first { it.orderId == orderId }
        assertAll(
            { assertThat(purchased.captured.map { it.key() }).contains(orderId.toString()) },
            { assertThat(tracked.captured.map { it.key() }).contains(orderId.toString()) },
            { assertThat(purchased.captured.first().topic()).isEqualTo(topic) },
            { assertThat(delivered.eventType).isEqualTo("OrderConfirmed") },
            { assertThat(delivered.items).containsExactly(OrderConfirmedEvent.Item(productId = 7, quantity = 2)) },
        )
    }

    companion object {
        private const val POLL_MS = 200L
        private const val ASSIGNMENT_TIMEOUT_NANOS = 60_000_000_000L

        private const val DELIVERY_TIMEOUT_MS = 30_000L
    }
}
