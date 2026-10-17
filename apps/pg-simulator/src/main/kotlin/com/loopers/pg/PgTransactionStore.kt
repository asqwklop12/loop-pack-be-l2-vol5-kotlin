package com.loopers.pg

import org.springframework.stereotype.Component
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PgTransactionStore {
    private val byKey = ConcurrentHashMap<String, PgTransaction>()
    private val keyByOrderId = ConcurrentHashMap<Long, String>()

    fun accept(orderId: Long, amount: BigInteger, at: Instant): Accepted {
        var createdKey: String? = null

        val key = keyByOrderId.computeIfAbsent(orderId) {
            val generated = "pgtx_" + UUID.randomUUID().toString().replace("-", "").take(20)
            byKey[generated] = PgTransaction(
                transactionKey = generated,
                orderId = orderId,
                amount = amount,
                status = PgStatus.PENDING,
                requestedAt = at,
            )
            createdKey = generated
            generated
        }

        return Accepted(transaction = byKey.getValue(key), isNew = createdKey != null)
    }

    fun find(transactionKey: String): PgTransaction? = byKey[transactionKey]

    fun findByOrderId(orderId: Long): PgTransaction? = keyByOrderId[orderId]?.let { byKey[it] }

    fun settle(transactionKey: String, next: PgStatus, at: Instant): PgTransaction? =
        byKey.compute(transactionKey) { _, current -> current?.settle(next, at) }

    data class Accepted(
        val transaction: PgTransaction,
        val isNew: Boolean,
    )
}
