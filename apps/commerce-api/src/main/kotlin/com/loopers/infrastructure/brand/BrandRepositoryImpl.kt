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

    override fun findAll(): List<Brand> = brandJpaRepository.findAllByDeletedAtIsNull()

    override fun findAllByIds(ids: List<Long>): List<Brand> =
        if (ids.isEmpty()) emptyList() else brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
}
