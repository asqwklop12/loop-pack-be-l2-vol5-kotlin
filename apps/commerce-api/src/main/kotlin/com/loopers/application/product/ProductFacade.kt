package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component

/**
 * 상품·브랜드·좋아요 수를 조합한다. 조회 결과의 구성은 application 의 책임이다.
 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
) {
    fun getProduct(productId: Long): ProductInfo {
        val product = productService.get(productId)
        val brand = brandService.get(product.brandId)

        return ProductInfo.of(product = product, brand = brand, likeCount = likeService.countOf(product.id))
    }
}
