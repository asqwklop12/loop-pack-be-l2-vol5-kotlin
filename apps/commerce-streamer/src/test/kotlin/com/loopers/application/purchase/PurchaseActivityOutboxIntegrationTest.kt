package com.loopers.application.purchase

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxStatus
import com.loopers.domain.purchase.PurchaseActivityRepository
import com.loopers.domain.purchase.PurchaseConflictException
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository
import com.loopers.interfaces.consumer.order.PurchaseActivityConsumer
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.support.Acknowledgment
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.kafka.listener.auto-startup=false"])
class PurchaseActivityOutboxIntegrationTest @Autowired constructor(
    private val consumer: PurchaseActivityConsumer,
    private val purchaseActivityRepository: PurchaseActivityRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @SpykBean
    private lateinit var outboxEventRepository: OutboxEventRepository

    private val acknowledgment = mockk<Acknowledgment>(relaxed = true)

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun payload(orderId: Long, quantity: Int = 2) =
        """{"eventId":"order-confirmed:v1:$orderId","orderId":$orderId,"userId":7,""" +
            """"confirmedAt":"2026-11-04T01:00:00Z","items":[{"productId":10,"quantity":$quantity}],""" +
            """"eventType":"OrderConfirmed","schemaVersion":1}"""

    private fun pendingRow(orderId: Long): OutboxEvent = outboxEventRepository.save(
        OutboxEvent(
            eventId = "order-confirmed:v1:$orderId",
            topic = "order.events.v1",
            eventType = "OrderConfirmed",
            schemaVersion = 1,
            payload = payload(orderId),
            key = orderId.toString(),
        ),
    )

    private fun consume(orderId: Long, quantity: Int = 2) = consumer.consume(
        ConsumerRecord("order.events.v1", 0, 0L, orderId.toString(), payload(orderId, quantity).toByteArray()),
        acknowledgment,
    )

    private fun statusOf(orderId: Long) =
        outboxEventJpaRepository.findAll().single { it.eventId == "order-confirmed:v1:$orderId" }.status

    @DisplayName("구매 활동을 반영하면 같은 이벤트의 아웃박스 행이 SENT 가 된다.")
    @Test
    fun marksSentWhenReflected() {
        pendingRow(1L)

        consume(1L)

        assertAll(
            { assertThat(statusOf(1L)).isEqualTo(OutboxStatus.SENT) },
            { assertThat(purchaseActivityRepository.findAllByOrderId(1L)).hasSize(1) },
        )
    }

    @DisplayName("이미 반영한 이벤트가 다시 와도 아웃박스 행을 SENT 로 바꾼다.")
    @Test
    fun marksSentOnDuplicate() {
        consume(1L)
        pendingRow(1L)

        consume(1L)

        assertThat(statusOf(1L)).isEqualTo(OutboxStatus.SENT)
    }

    @DisplayName("같은 eventId 로 다른 내용이 오면 SENT 로 바꾸지 않는다.")
    @Test
    fun keepsPendingOnConflict() {
        consume(1L, quantity = 2)
        pendingRow(1L)

        assertThrows<PurchaseConflictException> { consume(1L, quantity = 99) }

        assertThat(statusOf(1L)).isEqualTo(OutboxStatus.PENDING)
    }

    @DisplayName("SENT 로 바꾸지 못하면 구매 활동 반영도 되돌아간다.")
    @Test
    fun rollsBackReflectionWhenMarkSentFails() {
        pendingRow(1L)
        every { outboxEventRepository.markSent(any()) } throws IllegalStateException("아웃박스 갱신 실패")

        runCatching { consume(1L) }

        assertAll(
            { assertThat(purchaseActivityRepository.findAllByOrderId(1L)).isEmpty() },
            { assertThat(statusOf(1L)).isEqualTo(OutboxStatus.PENDING) },
        )
    }
}
