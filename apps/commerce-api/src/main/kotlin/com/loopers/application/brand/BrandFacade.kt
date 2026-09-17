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

    fun getBrands(): List<BrandInfo> = brandService.getAll().map { BrandInfo.from(it) }

    fun createBrand(name: String): BrandInfo = BrandInfo.from(brandService.create(name))

    fun updateBrand(id: Long, name: String): BrandInfo = BrandInfo.from(brandService.update(id, name))

    fun deleteBrand(id: Long) = brandService.delete(id)
}
