package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 트랜잭션 경계는 이 계층이다. 유스케이스 하나가 트랜잭션 하나다.
 * 도메인 서비스에도 `@Transactional` 이 있지만 그것은 서비스를 직접 부르는 경로를 위한 것이고,
 * 여기서 시작한 트랜잭션에 참여한다.
 */
@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    @Transactional(readOnly = true)
    fun getBrand(id: Long): BrandInfo = BrandInfo.from(brandService.get(id))

    @Transactional(readOnly = true)
    fun getBrands(): List<BrandInfo> = brandService.getAll().map { BrandInfo.from(it) }

    @Transactional
    fun createBrand(name: String): BrandInfo = BrandInfo.from(brandService.create(name))

    @Transactional
    fun updateBrand(id: Long, name: String): BrandInfo = BrandInfo.from(brandService.update(id, name))

    @Transactional
    fun deleteBrand(id: Long) = brandService.delete(id)
}
