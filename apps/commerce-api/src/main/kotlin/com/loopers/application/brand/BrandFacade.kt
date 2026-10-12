package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
) {
    fun getBrand(id: Long): BrandInfo = BrandInfo.from(brandService.get(id))

    fun getBrands(): List<BrandInfo> = brandService.getAll().map { BrandInfo.from(it) }

    fun createBrand(name: String): BrandInfo = BrandInfo.from(brandService.create(name))

    fun updateBrand(id: Long, name: String): BrandInfo = BrandInfo.from(brandService.update(id, name))

    /**
     * 상품이 남아 있어도 삭제하고, 그 브랜드의 상품을 모두 판매중지로 바꾼다.
     * 중간에 실패하면 이번 요청의 브랜드·상품 변경이 전부 취소되어야 하므로 경계가 여기다.
     */
    @Transactional
    fun deleteBrand(id: Long) {
        productService.suspendAllByBrand(id)

        brandService.delete(id)
    }
}
