package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSearch
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryDslRepository: ProductQueryDslRepository,
) : ProductRepository {
    override fun save(product: Product): Product = productJpaRepository.save(product)

    override fun find(id: Long): Product? = productJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun countActiveByBrandId(brandId: Long): Long =
        productJpaRepository.countByBrandIdAndDeletedAtIsNull(brandId)

    override fun findAll(): List<Product> = productJpaRepository.findAllByDeletedAtIsNull()

    override fun findAllByIds(ids: List<Long>): List<Product> =
        if (ids.isEmpty()) emptyList() else productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)

    override fun search(search: ProductSearch): List<Product> = productQueryDslRepository.search(search)

    override fun countBy(brandId: Long?): Long = productQueryDslRepository.countBy(brandId)
}
