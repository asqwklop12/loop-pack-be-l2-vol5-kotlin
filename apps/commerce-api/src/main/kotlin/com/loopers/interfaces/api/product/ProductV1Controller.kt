package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductSearch
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) {
    @GetMapping
    fun getProducts(
        @RequestParam(value = "brandId", required = false) brandId: Long?,
        @RequestParam(value = "sort", required = false) sort: String?,
        @RequestParam(value = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(value = "size", required = false, defaultValue = "20") size: Int,
    ): ApiResponse<ProductV1Dto.ProductPageResponse> {
        val search = ProductSearch(
            brandId = brandId,
            sort = ProductSort.from(sort),
            page = page,
            size = size,
        )

        val result = productFacade.searchProducts(search)

        return ApiResponse.success(
            ProductV1Dto.ProductPageResponse(
                items = result.items.map { ProductV1Dto.ProductResponse.from(it) },
                page = result.page,
                size = result.size,
                totalCount = result.totalCount,
            ),
        )
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        return productFacade.getProduct(productId)
            .let { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
