package com.loopers.config.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.util.backoff.BackOff

private val log = LoggerFactory.getLogger(RetryThenStopErrorHandler::class.java)

private val alert = ConsumerRecordRecoverer { record, exception ->
    val cause = NestedExceptionUtils.getMostSpecificCause(exception)

    log.error(
        "재시도를 모두 쓰고 컨슈머를 멈춘다. group={} topic={} partition={} offset={} key={} cause={}: {}",
        (exception as? ListenerExecutionFailedException)?.groupId,
        record.topic(),
        record.partition(),
        record.offset(),
        record.key(),
        cause.javaClass.simpleName,
        cause.message,
        exception,
    )
}

class RetryThenStopErrorHandler(backOff: BackOff) : DefaultErrorHandler(alert, backOff) {
    private val stopping = CommonContainerStoppingErrorHandler()

    override fun handleRemaining(
        thrownException: Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
    ) {
        super.handleRemaining(thrownException, records, consumer, container)
        stopping.handleRemaining(thrownException, records, consumer, container)
    }
}
