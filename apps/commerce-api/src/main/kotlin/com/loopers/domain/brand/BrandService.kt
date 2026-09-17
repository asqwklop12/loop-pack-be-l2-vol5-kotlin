package com.loopers.domain.brand

import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
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

    /**
     * 삭제되지 않은 상품이 하나라도 남아 있으면 거절한다. 재고가 0인 상품도 남아 있는 상품이다.
     */
    @Transactional
    fun delete(id: Long) {
        val brand = get(id)

        val activeProducts = productRepository.countActiveByBrandId(id)
        if (activeProducts > 0) {
            throw CoreException(ErrorType.CONFLICT, "연결된 상품이 남아 있어 삭제할 수 없습니다. [상품 수 = $activeProducts]")
        }

        brand.delete()
    }
}
