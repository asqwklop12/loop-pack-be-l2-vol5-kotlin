package com.loopers.config.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.util.backoff.BackOff

class RetryThenStopErrorHandler(backOff: BackOff) : DefaultErrorHandler(backOff) {
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
