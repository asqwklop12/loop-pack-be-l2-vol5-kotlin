package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
class LikeV1Controller(
    private val likeFacade: LikeFacade,
) {
    @PostMapping
    fun like(
        @RequestHeader(value = "X-USER-ID") userId: Long,
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<Unit> {
        likeFacade.like(userId, productId)

        return ApiResponse.success(Unit)
    }

    @DeleteMapping
    fun unlike(
        @RequestHeader(value = "X-USER-ID") userId: Long,
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<Unit> {
        likeFacade.unlike(userId, productId)

        return ApiResponse.success(Unit)
    }
}
