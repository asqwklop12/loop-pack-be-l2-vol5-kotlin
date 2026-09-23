package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product

data class ProductInfo(
    val id: Long,
    val name: String,
    val price: Long,
    val stock: Int,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
) {
    companion object {
        fun of(product: Product, brand: Brand, likeCount: Long): ProductInfo {
            return ProductInfo(
                id = product.id,
                name = product.name,
                price = product.price.amount,
                stock = product.stock.quantity,
                brandId = brand.id,
                brandName = brand.name,
                likeCount = likeCount,
            )
        }
    }
}
