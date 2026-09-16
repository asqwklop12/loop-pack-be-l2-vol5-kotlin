package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun find(id: Long): Brand?
    fun findByName(name: String): Brand?
    fun findAll(): List<Brand>
}
