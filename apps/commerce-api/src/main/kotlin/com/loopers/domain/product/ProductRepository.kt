package com.loopers.domain.product

interface ProductRepository {
    fun save(product: Product): Product
    fun find(id: Long): Product?
    fun countActiveByBrandId(brandId: Long): Long
    fun findAll(): List<Product>
    fun findAllByIds(ids: List<Long>): List<Product>
    fun search(search: ProductSearch): List<Product>
    fun countBy(brandId: Long?): Long
}
