package com.loopers.interfaces.api.admin

import com.loopers.application.brand.BrandFacade
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
@RequestMapping("/api-admin/v1/brands")
class BrandV1AdminController(
    private val brandFacade: BrandFacade,
) {
    @GetMapping
    fun getBrands(
        @RequestHeader(value = "X-USER-ROLE") role: String,
    ): ApiResponse<List<AdminV1Dto.BrandV1.Response>> {
        AdminRole.guard(role)

        return brandFacade.getBrands()
            .map { AdminV1Dto.BrandV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{brandId}")
    fun getBrand(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "brandId") brandId: Long,
    ): ApiResponse<AdminV1Dto.BrandV1.Response> {
        AdminRole.guard(role)

        return brandFacade.getBrand(brandId)
            .let { AdminV1Dto.BrandV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{brandId}")
    fun updateBrand(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "brandId") brandId: Long,
        @RequestBody request: AdminV1Dto.BrandV1.UpdateRequest,
    ): ApiResponse<AdminV1Dto.BrandV1.Response> {
        AdminRole.guard(role)

        return brandFacade.updateBrand(brandId, request.name)
            .let { AdminV1Dto.BrandV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    fun createBrand(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @RequestBody request: AdminV1Dto.BrandV1.CreateRequest,
    ): ApiResponse<AdminV1Dto.BrandV1.Response> {
        AdminRole.guard(role)

        return brandFacade.createBrand(request.name)
            .let { AdminV1Dto.BrandV1.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{brandId}")
    fun deleteBrand(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "brandId") brandId: Long,
    ): ApiResponse<Unit> {
        AdminRole.guard(role)
        brandFacade.deleteBrand(brandId)

        return ApiResponse.success(Unit)
    }
}
