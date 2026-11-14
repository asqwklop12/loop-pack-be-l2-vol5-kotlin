package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class MyCouponV1Controller(
    private val couponFacade: CouponFacade,
) {
    @GetMapping
    fun getMyCoupons(
        @RequestHeader(value = "X-USER-ID") userId: Long,
    ): ApiResponse<List<CouponV1Dto.Response>> {
        return couponFacade.getMyCoupons(userId)
            .map { CouponV1Dto.Response.from(it) }
            .let { ApiResponse.success(it) }
    }
}
