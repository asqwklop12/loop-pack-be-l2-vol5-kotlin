package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Product?
    fun countByBrandIdAndDeletedAtIsNull(brandId: Long): Long
    fun findAllByBrandIdAndDeletedAtIsNull(brandId: Long): List<Product>
    fun findAllByDeletedAtIsNull(): List<Product>
    fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<Product>

    @Modifying
    @Query(
        """
        update Product p
           set p.status = SUSPENDED
         where p.brandId = :brandId
           and p.deletedAt is null
           and p.status = SELLING
        """,
    )
    fun suspendAllByBrandId(@Param("brandId") brandId: Long): Int
}
