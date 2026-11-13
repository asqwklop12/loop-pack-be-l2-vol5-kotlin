package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.coupon.CouponIssueResult
import com.loopers.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupon-campaigns")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
) {
    @PostMapping("/{campaignId}/issues")
    fun issue(
        @RequestHeader(value = "X-USER-ID") userId: Long,
        @PathVariable(value = "campaignId") campaignId: Long,
    ): ResponseEntity<ApiResponse<Any>> =
        when (val result = couponFacade.issue(campaignId, userId)) {
            is CouponIssueResult.Issued ->
                ResponseEntity.ok(ApiResponse.success(CouponV1Dto.Response.from(result.coupon)))
            is CouponIssueResult.Accepted ->
                ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(CouponV1Dto.Accepted.from(result)))
        }
}
