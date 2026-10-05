package com.loopers.interfaces.api.user

import com.loopers.application.product.ProductInfo

class UserLikeV1Dto {
    /**
     * 좋아요 목록은 카탈로그가 아니라 **내 기록**이라 판매중지 상품도 남는다.
     * 그래서 다른 고객 응답과 달리 `sellable` 을 함께 준다. 화면이 이것으로 클릭을 막는다.
     */
    data class LikedProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
        val sellable: Boolean,
    ) {
        companion object {
            fun from(info: ProductInfo) = LikedProductResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                stock = info.stock,
                brandId = info.brandId,
                brandName = info.brandName,
                likeCount = info.likeCount,
                sellable = info.sellable,
            )
        }
    }
}
