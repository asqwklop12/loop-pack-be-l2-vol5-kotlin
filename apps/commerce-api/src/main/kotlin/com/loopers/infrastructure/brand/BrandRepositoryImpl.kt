package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand = brandJpaRepository.save(brand)

    override fun find(id: Long): Brand? = brandJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByName(name: String): Brand? = brandJpaRepository.findByNameAndDeletedAtIsNull(name)

    override fun findAll(): List<Brand> = brandJpaRepository.findAllByDeletedAtIsNull()
}
