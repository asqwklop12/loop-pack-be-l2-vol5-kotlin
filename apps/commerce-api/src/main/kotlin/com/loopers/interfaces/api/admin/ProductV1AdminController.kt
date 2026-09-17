package com.loopers.interfaces.api.admin

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/products")
class ProductV1AdminController(
    private val productFacade: ProductFacade,
) {
    @GetMapping
    fun getProducts(
        @RequestHeader(value = "X-USER-ROLE") role: String,
    ): ApiResponse<List<AdminV1Dto.ProductV1.Response>> {
        AdminRole.guard(role)

        return productFacade.getProducts()
            .map { AdminV1Dto.ProductV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    fun createProduct(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @RequestBody request: AdminV1Dto.ProductV1.CreateRequest,
    ): ApiResponse<AdminV1Dto.ProductV1.Response> {
        AdminRole.guard(role)

        return productFacade.createProduct(request.brandId, request.name, request.price)
            .let { AdminV1Dto.ProductV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<AdminV1Dto.ProductV1.Response> {
        AdminRole.guard(role)

        return productFacade.getProduct(productId)
            .let { AdminV1Dto.ProductV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{productId}")
    fun updateProduct(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "productId") productId: Long,
        @RequestBody request: AdminV1Dto.ProductV1.UpdateRequest,
    ): ApiResponse<AdminV1Dto.ProductV1.Response> {
        AdminRole.guard(role)

        return productFacade.updateProduct(productId, request.name, request.price)
            .let { AdminV1Dto.ProductV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{productId}/stock")
    fun changeStock(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "productId") productId: Long,
        @RequestBody request: AdminV1Dto.ProductV1.ChangeStockRequest,
    ): ApiResponse<AdminV1Dto.ProductV1.Response> {
        AdminRole.guard(role)

        return productFacade.changeStock(productId, request.amount)
            .let { AdminV1Dto.ProductV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

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
