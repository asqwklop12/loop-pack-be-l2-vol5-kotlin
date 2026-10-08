package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun create(name: String): Brand {
        return brandRepository.save(Brand(name = name))
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Brand {
        return brandRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 브랜드를 찾을 수 없습니다.")
    }

    @Transactional(readOnly = true)
    fun getAll(): List<Brand> = brandRepository.findAll()

    @Transactional(readOnly = true)
    fun getAllByIds(ids: List<Long>): List<Brand> = brandRepository.findAllByIds(ids)

    @Transactional
    fun update(id: Long, name: String): Brand {
        val brand = get(id).also { it.update(name) }

        return brandRepository.save(brand)
    }

    /**
     * 상품이 남아 있어도 삭제한다. 브랜드 폐쇄는 상품 수와 무관한 판단이기 때문이다.
     * 그 브랜드 상품을 판매중지로 바꾸는 일은 다른 도메인이라 퍼사드가 한다.
     */
    @Transactional
    fun delete(id: Long) {
        val brand = get(id).also { it.delete() }

        brandRepository.save(brand)
    }
}
