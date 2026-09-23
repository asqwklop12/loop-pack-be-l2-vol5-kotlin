package com.loopers.interfaces.api.user

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/{userId}/likes")
class UserLikeV1Controller(
    private val productFacade: ProductFacade,
) {
    @GetMapping
    fun getLikedProducts(
        @RequestHeader(value = "X-USER-ID") requesterId: Long,
        @PathVariable(value = "userId") userId: Long,
    ): ApiResponse<List<ProductV1Dto.ProductResponse>> {
        if (requesterId != userId) {
            throw CoreException(ErrorType.FORBIDDEN, "자신의 좋아요 목록만 조회할 수 있습니다.")
        }

        return productFacade.getLikedProducts(userId)
            .map { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
