package com.loopers.interfaces.api.point

import com.loopers.application.point.PointFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/points")
class PointV1Controller(
    private val pointFacade: PointFacade,
) {
    @PostMapping("/charge")
    fun charge(
        @RequestHeader(value = "X-USER-ID") userId: Long,
        @RequestBody request: PointV1Dto.ChargeRequest,
    ): ApiResponse<PointV1Dto.BalanceResponse> {
        return pointFacade.charge(userId, request.amount)
            .let { PointV1Dto.BalanceResponse(balance = it.balance) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    fun getBalance(
        @RequestHeader(value = "X-USER-ID") userId: Long,
    ): ApiResponse<PointV1Dto.BalanceResponse> {
        return pointFacade.getBalance(userId)
            .let { PointV1Dto.BalanceResponse(balance = it.balance) }
            .let { ApiResponse.success(it) }
    }
}
