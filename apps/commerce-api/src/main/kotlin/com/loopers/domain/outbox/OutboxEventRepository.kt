package com.loopers.domain.outbox

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent
}
