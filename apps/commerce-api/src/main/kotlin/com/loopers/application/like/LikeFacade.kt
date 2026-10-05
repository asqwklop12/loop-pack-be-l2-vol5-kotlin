package com.loopers.application.like

import com.loopers.domain.like.LikeService
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
) {
    fun like(userId: Long, productId: Long) = likeService.like(userId, productId)

    fun unlike(userId: Long, productId: Long) = likeService.unlike(userId, productId)
}
