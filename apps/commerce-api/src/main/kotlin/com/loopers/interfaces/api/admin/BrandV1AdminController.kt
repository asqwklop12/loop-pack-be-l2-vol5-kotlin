package com.loopers.interfaces.api.admin

import com.loopers.application.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/brands")
class BrandV1AdminController(
    private val brandFacade: BrandFacade,
) {
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
