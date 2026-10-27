package com.loopers.interfaces.consumer.order

import com.loopers.domain.purchase.ProductPurchaseActivity
import com.loopers.domain.purchase.PurchaseActivityRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
    private val purchaseActivityRepository: PurchaseActivityRepository,
    private val registry: KafkaListenerEndpointRegistry,
    @Value("\${order-event.topic}") private val topic: String,
    private val databaseCleanUp: DatabaseCleanUp,
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

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun eventOf(orderId: Long, vararg items: Pair<Long, Int>) = OrderConfirmedEvent(
        orderId = orderId,
        userId = BUYER,
        items = items.map { OrderConfirmedEvent.Item(productId = it.first, quantity = it.second) },
        eventType = "OrderConfirmed",
    )

    private fun activitiesOf(orderId: Long): List<ProductPurchaseActivity> {
        val deadline = System.nanoTime() + DELIVERY_TIMEOUT_NANOS

        while (System.nanoTime() < deadline) {
            val found = purchaseActivityRepository.findAllByOrderId(orderId)
            if (found.isNotEmpty()) return found

            Thread.sleep(POLL_MS)
        }

        return emptyList()
    }

    @DisplayName("한 토픽의 같은 메시지를 두 컨슈머 그룹이 각자 받는다.")
    @Test
    fun bothGroupsReceiveSameRecord() {
        val orderId = System.currentTimeMillis()
        val purchased = slot<List<ConsumerRecord<String, String>>>()
        val tracked = slot<List<ConsumerRecord<String, String>>>()

        kafkaTemplate.send(topic, orderId.toString(), eventOf(orderId, 7L to 2)).get()

        verify(timeout = DELIVERY_TIMEOUT_MS) {
            purchaseActivityConsumer.consume(capture(purchased), any<Acknowledgment>())
            stockTrackingConsumer.consume(capture(tracked), any<Acknowledgment>())
        }
        assertAll(
            { assertThat(purchased.captured.map { it.key() }).contains(orderId.toString()) },
            { assertThat(tracked.captured.map { it.key() }).contains(orderId.toString()) },
            { assertThat(purchased.captured.first().topic()).isEqualTo(topic) },
        )
    }

    @DisplayName("주문 확정 이벤트를 받으면 품목마다 구매 활동 한 행을 남긴다.")
    @Test
    fun recordsOneRowPerItem() {
        val orderId = System.currentTimeMillis()

        kafkaTemplate.send(topic, orderId.toString(), eventOf(orderId, 7L to 2, 8L to 1)).get()

        val activities = activitiesOf(orderId)
        assertAll(
            { assertThat(activities).hasSize(2) },
            { assertThat(activities.map { it.productId }).containsExactly(7L, 8L) },
            { assertThat(activities.map { it.quantity }).containsExactly(2, 1) },
            { assertThat(activities).allMatch { it.orderId == orderId } },
            { assertThat(activities).allMatch { it.userId == BUYER } },
        )
    }

    companion object {
        private const val BUYER = 42L
        private const val POLL_MS = 200L
        private const val ASSIGNMENT_TIMEOUT_NANOS = 60_000_000_000L
        private const val DELIVERY_TIMEOUT_NANOS = 30_000_000_000L
        private const val DELIVERY_TIMEOUT_MS = 30_000L
    }
}
