package com.loopers.domain.like

import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val productService: ProductService,
) {
    /**
     * 관계가 없을 때만 저장한다. 이미 눌렀어도 성공으로 끝난다.
     */
    @Transactional
    fun like(userId: Long, productId: Long) {
        productService.get(productId)

        likeRepository.find(userId, productId)
            ?: likeRepository.save(Like(userId = userId, productId = productId))
    }

    /**
     * 자신의 관계가 있을 때만 지운다. 누른 적이 없어도 성공으로 끝난다.
     * 등록과 달리 상품의 삭제 여부를 보지 않는다. 삭제된 상품에 남은 자신의 관계도 취소할 수 있어야 한다.
     */
    @Transactional
    fun unlike(userId: Long, productId: Long) {
        likeRepository.find(userId, productId)
            ?.let { likeRepository.delete(it) }
    }

    @Transactional(readOnly = true)
    fun countOf(productId: Long): Long = likeRepository.countByProductId(productId)

    @Transactional(readOnly = true)
    fun likedProductIds(userId: Long): List<Long> =
        likeRepository.findAllByUserId(userId).map { it.productId }
}
