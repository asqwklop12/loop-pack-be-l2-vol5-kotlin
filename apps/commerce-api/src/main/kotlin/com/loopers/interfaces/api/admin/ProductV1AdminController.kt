package com.loopers.interfaces.api.admin

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/products")
class ProductV1AdminController(
    private val productFacade: ProductFacade,
) {
    @DeleteMapping("/{productId}")
    fun deleteProduct(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<Unit> {
        AdminRole.guard(role)
        productFacade.deleteProduct(productId)

        return ApiResponse.success(Unit)
    }
}
