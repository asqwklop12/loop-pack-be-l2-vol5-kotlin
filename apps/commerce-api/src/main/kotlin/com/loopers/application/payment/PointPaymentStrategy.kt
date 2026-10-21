package com.loopers.application.payment

import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentResult
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PaymentStrategy
import com.loopers.domain.point.PointService
import org.springframework.stereotype.Component

@Component
class PointPaymentStrategy(
    private val pointService: PointService,
) : PaymentStrategy {
    override val external: Boolean = false

    override fun supports(method: PaymentMethod): Boolean = method == PaymentMethod.POINT

    override fun pay(command: PaymentCommand): PaymentResult {
        pointService.pay(command.userId, command.amount)

        return PaymentResult(method = PaymentMethod.POINT, status = PaymentStatus.SUCCESS)
    }
}
