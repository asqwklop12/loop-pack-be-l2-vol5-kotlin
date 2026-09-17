package com.loopers.interfaces.api.product

class ProductV1Dto {
    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    )
}
