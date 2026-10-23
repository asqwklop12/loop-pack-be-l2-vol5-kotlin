package com.loopers.application.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PaymentTransaction
import com.loopers.domain.point.PointService
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class PaymentProcessorTest {
    private val pointService = mockk<PointService>(relaxed = true)
    private val paymentGateway = mockk<PaymentGateway>()
    private val pointStrategy = PointPaymentStrategy(pointService)
    private val cardStrategy = CardPaymentStrategy(paymentGateway)
    private val processor = PaymentProcessor(listOf(pointStrategy, cardStrategy))

    private val card = CardInfo(type = "SAMSUNG", number = "1234-5678-9814-1451")
    private val amount = Money(50_000)

    private fun command(method: PaymentMethod, card: CardInfo? = null) = PaymentCommand(
        userId = 1L,
        orderId = 100L,
        amount = amount,
        method = method,
        card = card,
    )

    private fun pending() = PaymentTransaction(
        orderId = 100L,
        amount = amount,
        maskedCardNo = "****-****-****-1451",
        status = PaymentStatus.PENDING,
    )

    @DisplayName("수단을 고를 때,")
    @Nested
    inner class Select {
        @DisplayName("포인트는 포인트 전략이 맡는다.")
        @Test
        fun routesToPoint() {
            val result = processor.pay(command(PaymentMethod.POINT))

            assertAll(
                { verify(exactly = 1) { pointService.pay(1L, amount) } },
                { verify(exactly = 0) { paymentGateway.request(any(), any(), any()) } },
                { assertThat(result.method).isEqualTo(PaymentMethod.POINT) },
            )
        }

        @DisplayName("카드는 PG 전략이 맡는다.")
        @Test
        fun routesToCard() {
            every { paymentGateway.request(100L, amount, card) } returns pending()

            val result = processor.pay(command(PaymentMethod.CARD, card))

            assertAll(
                { verify(exactly = 1) { paymentGateway.request(100L, amount, card) } },
                { verify(exactly = 0) { pointService.pay(any(), any()) } },
                { assertThat(result.method).isEqualTo(PaymentMethod.CARD) },
            )
        }

        @DisplayName("맡을 전략이 없으면 거절한다.")
        @Test
        fun rejectsUnsupportedMethod() {
            val onlyPoint = PaymentProcessor(listOf(pointStrategy))

            assertThatThrownBy { onlyPoint.pay(command(PaymentMethod.CARD, card)) }
                .isInstanceOf(CoreException::class.java)
                .extracting { (it as CoreException).errorType }
                .isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("결제 결과는,")
    @Nested
    inner class Result {
        @DisplayName("포인트는 그 자리에서 확정된다.")
        @Test
        fun pointIsFinal() {
            val result = processor.pay(command(PaymentMethod.POINT))

            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(result.isFinal).isTrue() },
            )
        }

        @DisplayName("카드는 PG 가 준 상태를 그대로 전한다. 미확정일 수 있다.")
        @Test
        fun cardMayStayPending() {
            every { paymentGateway.request(any(), any(), any()) } returns pending()

            val result = processor.pay(command(PaymentMethod.CARD, card))

            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(result.isFinal).isFalse() },
            )
        }
    }

    @DisplayName("카드 결제인데 카드 정보가 없으면 거절한다.")
    @Test
    fun rejectsCardWithoutCardInfo() {
        assertThatThrownBy { processor.pay(command(PaymentMethod.CARD)) }
            .isInstanceOf(CoreException::class.java)
            .hasMessageContaining("카드 정보")

        verify(exactly = 0) { paymentGateway.request(any(), any(), any()) }
    }
}
