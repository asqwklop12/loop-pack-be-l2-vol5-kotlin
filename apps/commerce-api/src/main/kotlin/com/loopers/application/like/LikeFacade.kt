package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
) {
    /**
     * 살 수 있는 상품에만 누를 수 있다. 상품 확인은 다른 도메인이라 여기서 한다.
     */
    @Transactional
    fun like(userId: Long, productId: Long) {
        productService.getSellable(productId)

        likeService.like(userId, productId)
    }

    /**
     * 등록과 달리 상품을 보지 않는다. 삭제된 상품에 남은 자신의 관계도 취소할 수 있어야 한다.
     */
    fun unlike(userId: Long, productId: Long) = likeService.unlike(userId, productId)
}
