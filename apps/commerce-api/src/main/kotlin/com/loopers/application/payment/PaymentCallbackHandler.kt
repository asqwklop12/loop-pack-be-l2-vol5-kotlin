package com.loopers.application.payment

import com.loopers.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * PG 가 결제 결과를 알려오는 지점이다. 지금은 **받아서 남기기만 한다.**
 *
 * 주문 상태를 바꾸지 않는 이유는 받을 준비가 안 돼서다. 세 가지가 없다.
 * - 결제 시도 기록이 없어 같은 콜백이 두 번 와도 구별하지 못한다 (S-6)
 * - 종단 상태 뒤에 온 PENDING 을 걸러낼 근거가 없다 (S-7)
 * - FAILED 일 때 이미 확정된 주문을 어떻게 할지 정해지지 않았다
 *
 * 그래서 반영을 흉내 내는 대신 비워 둔다. 반쯤 반영하면 돈 사고가 조용히 생긴다.
 */
@Component
class PaymentCallbackHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(callback: PaymentCallback) {
        when (callback.status) {
            PaymentStatus.SUCCESS -> log.info(
                "결제 승인 콜백. orderId={} transactionKey={}",
                callback.orderId,
                callback.transactionKey,
            )

            PaymentStatus.FAILED -> log.warn(
                "결제 거절 콜백. orderId={} transactionKey={} code={} reason={}",
                callback.orderId,
                callback.transactionKey,
                callback.declineCode,
                callback.declineReason,
            )

            PaymentStatus.PENDING -> log.info(
                "미확정 콜백. orderId={} transactionKey={}",
                callback.orderId,
                callback.transactionKey,
            )
        }
    }
}
