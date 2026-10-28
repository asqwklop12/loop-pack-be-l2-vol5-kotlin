package com.loopers.config.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.util.backoff.BackOff

private val log = LoggerFactory.getLogger(RetryThenDeadLetterErrorHandler::class.java)

private fun quarantining(deadLetters: KafkaOperations<*, *>): ConsumerRecordRecoverer {
    val publishing = DeadLetterPublishingRecoverer(deadLetters)

    return ConsumerRecordRecoverer { record, exception ->
        publishing.accept(record, exception)
        alert(record, exception)
    }
}

private fun alert(record: ConsumerRecord<*, *>, exception: Exception) {
    val cause = NestedExceptionUtils.getMostSpecificCause(exception)

    log.error(
        "재시도를 모두 쓰고 DLT 로 보냈다. group={} topic={} partition={} offset={} key={} cause={}: {}",
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

class RetryThenDeadLetterErrorHandler(
    deadLetters: KafkaOperations<*, *>,
    backOff: BackOff,
) : DefaultErrorHandler(quarantining(deadLetters), backOff) {
    init {
        setCommitRecovered(true)
    }

    companion object {
        const val TOPIC_SUFFIX = "-dlt"
    }
}
