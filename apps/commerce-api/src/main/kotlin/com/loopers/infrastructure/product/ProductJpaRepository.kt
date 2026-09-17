package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Product?
    fun countByBrandIdAndDeletedAtIsNull(brandId: Long): Long
    fun findAllByDeletedAtIsNull(): List<Product>
    fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<Product>
}
