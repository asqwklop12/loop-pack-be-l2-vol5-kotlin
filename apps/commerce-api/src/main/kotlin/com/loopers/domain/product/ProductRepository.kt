package com.loopers.domain.product

interface ProductRepository {
    fun save(product: Product): Product
    fun find(id: Long): Product?
    fun countActiveByBrandId(brandId: Long): Long
}
