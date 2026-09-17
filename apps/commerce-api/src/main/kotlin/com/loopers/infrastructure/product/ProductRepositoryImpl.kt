package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product = productJpaRepository.save(product)

    override fun find(id: Long): Product? = productJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun countActiveByBrandId(brandId: Long): Long =
        productJpaRepository.countByBrandIdAndDeletedAtIsNull(brandId)
}
