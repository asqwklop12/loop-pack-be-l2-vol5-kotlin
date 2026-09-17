package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    fun getBrand(id: Long): BrandInfo {
        return brandService.get(id)
            .let { BrandInfo.from(it) }
    }

    fun deleteBrand(id: Long) = brandService.delete(id)
}
