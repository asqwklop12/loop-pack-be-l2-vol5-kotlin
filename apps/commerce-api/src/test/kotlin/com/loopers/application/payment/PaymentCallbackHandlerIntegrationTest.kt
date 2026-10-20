package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.shared.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PaymentCallbackHandlerIntegrationTest @Autowired constructor(
    private val paymentCallbackHandler: PaymentCallbackHandler,
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val orderId = 1001L

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun pendingPayment(orderId: Long = this.orderId) =
        paymentRepository.save(Payment(orderId = orderId, amount = Money(50_000)))

    private fun callback(
        status: PaymentStatus,
        orderId: Long = this.orderId,
        transactionKey: String = "pgtx_1",
    ) = PaymentCallback(
        transactionKey = transactionKey,
        orderId = orderId,
        status = status,
        declineCode = if (status == PaymentStatus.FAILED) "LIMIT_EXCEEDED" else null,
        declineReason = if (status == PaymentStatus.FAILED) "한도초과" else null,
    )

    private fun statusOf(orderId: Long = this.orderId) =
        paymentRepository.findAllByOrderId(orderId).first().status

    @DisplayName("승인 콜백이 오면 미확정 결제가 SUCCESS 가 된다.")
    @Test
    fun approvesPending() {
        pendingPayment()

        paymentCallbackHandler.handle(callback(PaymentStatus.SUCCESS))

        assertThat(statusOf()).isEqualTo(PaymentStatus.SUCCESS)
    }

    @DisplayName("같은 승인 콜백이 두 번 와도 결과가 같다.")
    @Test
    fun isIdempotent() {
        pendingPayment()

        paymentCallbackHandler.handle(callback(PaymentStatus.SUCCESS))
        paymentCallbackHandler.handle(callback(PaymentStatus.SUCCESS))

        assertAll(
            { assertThat(paymentRepository.findAllByOrderId(orderId)).hasSize(1) },
            { assertThat(statusOf()).isEqualTo(PaymentStatus.SUCCESS) },
        )
    }

    @DisplayName("다른 주문의 결제는 건드리지 않는다.")
    @Test
    fun touchesOnlyItsOwnOrder() {
        pendingPayment(orderId = 1001L)
        pendingPayment(orderId = 2002L)

        paymentCallbackHandler.handle(callback(PaymentStatus.SUCCESS, orderId = 1001L))

        assertAll(
            { assertThat(statusOf(1001L)).isEqualTo(PaymentStatus.SUCCESS) },
            { assertThat(statusOf(2002L)).isEqualTo(PaymentStatus.PENDING) },
        )
    }

    @DisplayName("바꿀 결제가 없어도 터지지 않는다.")
    @Test
    fun survivesMissingPayment() {
        paymentCallbackHandler.handle(callback(PaymentStatus.SUCCESS, orderId = 9999L))

        assertThat(paymentRepository.findAllByOrderId(9999L)).isEmpty()
    }

    @DisplayName("거절 콜백이 오면 미확정 결제가 FAILED 가 된다.")
    @Test
    fun declinesPending() {
        pendingPayment()

        paymentCallbackHandler.handle(callback(PaymentStatus.FAILED))

        assertThat(statusOf()).isEqualTo(PaymentStatus.FAILED)
    }

    @DisplayName("이미 SUCCESS 인 결제는 거절 콜백이 와도 되돌아가지 않는다.")
    @Test
    fun doesNotRevertSuccess() {
        pendingPayment().also { it.markSuccess() }.also { paymentRepository.save(it) }

        paymentCallbackHandler.handle(callback(PaymentStatus.FAILED))

        assertThat(statusOf()).isEqualTo(PaymentStatus.SUCCESS)
    }

    @DisplayName("이미 SUCCESS 인 결제는 미확정 콜백이 와도 되돌아가지 않는다.")
    @Test
    fun doesNotRevertSettled() {
        pendingPayment().also { it.markSuccess() }.also { paymentRepository.save(it) }

        paymentCallbackHandler.handle(callback(PaymentStatus.PENDING))

        assertThat(statusOf()).isEqualTo(PaymentStatus.SUCCESS)
    }
}
