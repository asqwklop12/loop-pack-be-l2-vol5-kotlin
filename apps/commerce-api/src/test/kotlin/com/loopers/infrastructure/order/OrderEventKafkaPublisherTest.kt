package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderConfirmedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

class OrderEventKafkaPublisherTest {
    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val publisher = OrderEventKafkaPublisher(kafkaTemplate, OrderEventProperties(TOPIC))

    private val event = OrderConfirmedEvent(
        orderId = 42L,
        items = listOf(OrderConfirmedEvent.Item(productId = 1L, quantity = 3)),
    )

    @DisplayName("설정한 토픽으로 보내고 주문 id 를 키로 쓴다.")
    @Test
    fun sendsWithOrderIdKey() {
        val topic = slot<String>()
        val key = slot<Any>()
        val value = slot<Any>()
        every { kafkaTemplate.send(capture(topic), capture(key), capture(value)) } returns
            CompletableFuture.completedFuture(mockk<SendResult<Any, Any>>())

        publisher.publish(event)

        assertAll(
            { assertThat(topic.captured).isEqualTo(TOPIC) },
            { assertThat(key.captured).isEqualTo("42") },
            { assertThat(value.captured).isEqualTo(event) },
        )
    }

    @DisplayName("전송이 그 자리에서 터져도 호출한 쪽으로 올리지 않는다.")
    @Test
    fun swallowsSynchronousFailure() {
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws
            IllegalStateException("브로커 메타데이터를 못 받았다")

        assertThatCode { publisher.publish(event) }.doesNotThrowAnyException()
    }

    @DisplayName("전송이 나중에 실패해도 호출한 쪽으로 올리지 않는다.")
    @Test
    fun swallowsAsynchronousFailure() {
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns
            CompletableFuture.failedFuture(IllegalStateException("브로커가 응답하지 않는다"))

        assertAll(
            { assertThatCode { publisher.publish(event) }.doesNotThrowAnyException() },
            { verify(exactly = 1) { kafkaTemplate.send(any<String>(), any(), any()) } },
        )
    }

    companion object {
        private const val TOPIC = "order.events.v1"
    }
}
