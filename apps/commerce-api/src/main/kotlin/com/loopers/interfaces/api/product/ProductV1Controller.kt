package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) {
    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val info = productFacade.getProduct(productId)

        return ApiResponse.success(
            ProductV1Dto.ProductResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                stock = info.stock,
                brandId = info.brandId,
                brandName = info.brandName,
                likeCount = info.likeCount,
            ),
        )
    }
}
