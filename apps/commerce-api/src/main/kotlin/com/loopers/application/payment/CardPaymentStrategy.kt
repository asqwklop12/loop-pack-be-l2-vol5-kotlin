package com.loopers.application.payment

import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentResult
import com.loopers.domain.payment.PaymentStrategy
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class CardPaymentStrategy(
    private val paymentGateway: PaymentGateway,
) : PaymentStrategy {
    override fun supports(method: PaymentMethod): Boolean = method == PaymentMethod.CARD

    override fun pay(command: PaymentCommand): PaymentResult {
        val card = command.card
            ?: throw CoreException(ErrorType.BAD_REQUEST, "카드 결제에는 카드 정보가 필요합니다.")

        val transaction = paymentGateway.request(command.orderId, command.amount, card)

        return PaymentResult(
            method = PaymentMethod.CARD,
            status = transaction.status,
        )
    }
}
