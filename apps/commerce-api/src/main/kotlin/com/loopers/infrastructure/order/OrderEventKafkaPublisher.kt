package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderConfirmedEvent
import com.loopers.domain.order.OrderEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * 발행이 실패해도 주문은 이미 커밋돼 있다. 여기서 예외를 올리면 성공한 주문이 500 으로 나간다.
 * 그래서 삼키고 로그만 남긴다 — 주문을 살리는 대신 메시지를 잃는 쪽을 택한 것이다.
 */
@Component
class OrderEventKafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val properties: OrderEventProperties,
) : OrderEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: OrderConfirmedEvent) {
        try {
            kafkaTemplate.send(properties.topic, event.orderId.toString(), event)
                .whenComplete { _, failure -> failure?.let { log(event, it) } }
        } catch (blocked: Exception) {
            log(event, blocked)
        }
    }

    private fun log(event: OrderConfirmedEvent, failure: Throwable) {
        log.error(
            "주문 이벤트 발행 실패. topic={} type={} orderId={}",
            properties.topic,
            event.eventType,
            event.orderId,
            failure,
        )
    }
}
