package com.loopers.config.kafka

import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.KafkaException
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.util.backoff.ExponentialBackOff

class RetryThenStopErrorHandlerTest {
    private val container = mockk<MessageListenerContainer>(relaxed = true)
    private val consumer = mockk<Consumer<Any, Any>>(relaxed = true)
    private val handler = RetryThenStopErrorHandler(
        ExponentialBackOff().apply {
            initialInterval = 1L
            multiplier = 2.0
            maxAttempts = RETRIES
        },
    )

    @DisplayName("재시도가 남아 있으면 컨테이너를 멈추지 않는다.")
    @Test
    fun keepsRunningWhileRetriesRemain() {
        assertThatThrownBy { handle() }.isNotInstanceOf(KafkaException::class.java)

        verify(exactly = 0) { container.stopAbnormally(any()) }
    }

    @DisplayName("재시도를 모두 쓰면 컨테이너를 멈춘다.")
    @Test
    fun stopsWhenRetriesRunOut() {
        repeat(RETRIES) { runCatching { handle() } }

        assertThatThrownBy { handle() }.isInstanceOf(KafkaException::class.java)
        verify(timeout = 5_000) { container.stopAbnormally(any()) }
    }

    private fun handle() = handler.handleRemaining(
        RuntimeException("반영 실패"),
        mutableListOf(ConsumerRecord(TOPIC, 0, 7L, "key", "value")),
        consumer,
        container,
    )

    companion object {
        private const val TOPIC = "order.events.v1"
        private const val RETRIES = 2
    }
}
