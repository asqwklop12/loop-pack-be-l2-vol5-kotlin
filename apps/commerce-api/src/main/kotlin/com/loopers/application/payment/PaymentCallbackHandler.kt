package com.loopers.application.payment

import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentCallbackHandler(
    private val paymentRepository: PaymentRepository,
    private val paymentSettlement: PaymentSettlement,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(callback: PaymentCallback) {
        when (callback.status) {
            PaymentStatus.SUCCESS -> approve(callback)

            PaymentStatus.FAILED -> decline(callback)

            PaymentStatus.PENDING -> log.info(
                "미확정 콜백. orderId={} transactionKey={}",
                callback.orderId,
                callback.transactionKey,
            )
        }
    }

    private fun decline(callback: PaymentCallback) {
        val pending = pendingOf(callback.orderId)

        if (pending.isEmpty()) {
            log.info(
                "바꿀 미확정 결제가 없다. orderId={} transactionKey={}",
                callback.orderId,
                callback.transactionKey,
            )

            return
        }

        pending.forEach { paymentSettlement.reflect(it, PaymentStatus.FAILED) }

        log.warn(
            "결제 거절 반영. orderId={} transactionKey={} code={} reason={} count={}",
            callback.orderId,
            callback.transactionKey,
            callback.declineCode,
            callback.declineReason,
            pending.size,
        )
    }

    private fun approve(callback: PaymentCallback) {
        val pending = pendingOf(callback.orderId)

        if (pending.isEmpty()) {
            log.info(
                "바꿀 미확정 결제가 없다. orderId={} transactionKey={}",
                callback.orderId,
                callback.transactionKey,
            )

            return
        }

        pending.forEach { paymentSettlement.reflect(it, PaymentStatus.SUCCESS) }

        log.info(
            "결제 승인 반영. orderId={} transactionKey={} count={}",
            callback.orderId,
            callback.transactionKey,
            pending.size,
        )
    }

    private fun pendingOf(orderId: Long) =
        paymentRepository.findAllByOrderId(orderId).filter { it.status == PaymentStatus.PENDING }
}
