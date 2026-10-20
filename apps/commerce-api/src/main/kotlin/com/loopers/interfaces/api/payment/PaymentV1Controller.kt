package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCallback
import com.loopers.application.payment.PaymentCallbackHandler
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentCallbackHandler: PaymentCallbackHandler,
) {
    @PostMapping("/callback")
    fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Unit> {
        paymentCallbackHandler.handle(
            PaymentCallback(
                transactionKey = request.transactionKey,
                orderId = request.orderId,
                status = request.status,
                declineCode = request.declineCode,
                declineReason = request.declineReason,
                settledAt = request.settledAt,
            ),
        )

        return ApiResponse.success(Unit)
    }
}
