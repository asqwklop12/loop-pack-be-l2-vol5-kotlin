package com.loopers.pg

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/api/v1/payments")
class PgV1Controller(
    private val store: PgTransactionStore,
    private val callbackSender: PgCallbackSender,
) {
    @PostMapping
    fun request(
        @RequestBody request: PgV1Dto.PaymentRequest,
    ): ResponseEntity<PgV1Dto.PaymentResponse> {
        val accepted = store.accept(request.orderId, request.amount, Instant.now())

        if (accepted.isNew) {
            callbackSender.scheduleSettlement(accepted.transaction.transactionKey)
        }

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(PgV1Dto.PaymentResponse.from(accepted.transaction))
    }

    @GetMapping("/{transactionKey}")
    fun get(
        @PathVariable(value = "transactionKey") transactionKey: String,
    ): PgV1Dto.PaymentResponse =
        store.find(transactionKey)
            ?.let { PgV1Dto.PaymentResponse.from(it) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다. [transactionKey = $transactionKey]")

    @GetMapping
    fun getByOrderId(
        @RequestParam(value = "orderId") orderId: Long,
    ): PgV1Dto.PaymentResponse =
        store.findByOrderId(orderId)
            ?.let { PgV1Dto.PaymentResponse.from(it) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다. [orderId = $orderId]")
}
