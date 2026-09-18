package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun find(id: Long): Brand?
    fun findAll(): List<Brand>
    fun findAllByIds(ids: List<Long>): List<Brand>
}
