package com.loopers.domain.product

import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun create(brandId: Long, name: String, price: Money, stock: Stock): Product {
        requireExistingBrand(brandId)

        return productRepository.save(Product(brandId = brandId, name = name, price = price, stock = stock))
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Product {
        return productRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 상품을 찾을 수 없습니다.")
    }

    @Transactional
    fun update(id: Long, name: String, price: Money): Product {
        return get(id).also { it.update(name = name, price = price) }
    }

    @Transactional
    fun changeStock(id: Long, quantity: Int): Product {
        return get(id).also { it.changeStock(quantity) }
    }

    @Transactional
    fun delete(id: Long) {
        val product = get(id)
        if (!product.stock.isEmpty()) {
            throw CoreException(ErrorType.CONFLICT, "재고가 남아 있어 삭제할 수 없습니다. [재고 = ${product.stock}]")
        }

        product.delete()
    }

    private fun requireExistingBrand(brandId: Long) {
        brandRepository.find(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $brandId] 브랜드를 찾을 수 없습니다.")
    }
}
