package com.loopers.application.payment

import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentResult
import com.loopers.domain.payment.PaymentStrategy
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentProcessor(
    private val strategies: List<PaymentStrategy>,
) {
    fun pay(command: PaymentCommand): PaymentResult = select(command.method).pay(command)

    private fun select(method: PaymentMethod): PaymentStrategy =
        strategies.firstOrNull { it.supports(method) }
            ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 결제 수단입니다. [method = $method]")
}
