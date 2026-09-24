package com.loopers.application.like

import com.loopers.domain.like.LikeService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
) {
    @Transactional
    fun like(userId: Long, productId: Long) = likeService.like(userId, productId)

    @Transactional
    fun unlike(userId: Long, productId: Long) = likeService.unlike(userId, productId)
}
