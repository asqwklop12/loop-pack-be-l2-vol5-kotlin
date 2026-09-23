package com.loopers.interfaces.api.admin

import com.loopers.application.brand.BrandInfo
import com.loopers.application.product.ProductInfo

class AdminV1Dto {
    class BrandV1 {
        data class CreateRequest(val name: String)

        data class UpdateRequest(val name: String)

        data class Response(val id: Long, val name: String) {
            companion object {
                fun from(info: BrandInfo) = Response(id = info.id, name = info.name)
            }
        }
    }

    class ProductV1 {
        data class CreateRequest(
            val brandId: Long,
            val name: String,
            val price: Long,
        )

        data class UpdateRequest(
            val name: String,
            val price: Long,
        )

        data class ChangeStockRequest(val amount: Int)

        data class Response(
            val id: Long,
            val name: String,
            val price: Long,
            val stock: Int,
            val brandId: Long,
            val brandName: String,
            val likeCount: Long,
        ) {
            companion object {
                fun from(info: ProductInfo) = Response(
                    id = info.id,
                    name = info.name,
                    price = info.price,
                    stock = info.stock,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    likeCount = info.likeCount,
                )
            }
        }
    }
}
