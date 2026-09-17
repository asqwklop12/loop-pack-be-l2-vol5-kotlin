package com.loopers.domain.product

import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val likeRepository: LikeRepository,
) {
    /**
     * 재고는 생성에서 받지 않는다. 항상 0 으로 시작하고 재고 변경으로만 바꾼다.
     */
    @Transactional
    fun create(brandId: Long, name: String, price: Money): Product {
        requireExistingBrand(brandId)

        return productRepository.save(
            Product(brandId = brandId, name = name, price = price, stock = Stock(INITIAL_STOCK)),
        )
    }

    @Transactional(readOnly = true)
    fun getAll(): List<Product> = productRepository.findAll()

    @Transactional(readOnly = true)
    fun search(search: ProductSearch): List<Product> = productRepository.search(search)

    @Transactional(readOnly = true)
    fun countBy(brandId: Long?): Long = productRepository.countBy(brandId)

    /**
     * 삭제된 상품은 제외한다. 없는 id 는 조용히 빠진다.
     */
    @Transactional(readOnly = true)
    fun getAllByIds(ids: List<Long>): List<Product> = productRepository.findAllByIds(ids)

    @Transactional(readOnly = true)
    fun get(id: Long): Product {
        return productRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 상품을 찾을 수 없습니다.")
    }

    @Transactional
    fun update(id: Long, name: String, price: Money): Product {
        return get(id).also { it.update(name = name, price = price) }
    }

    /**
     * 최종 수량을 설정하지 않고 증가 값을 더한다. 음수만 거절하므로 0 은 재고를 바꾸지 않는다.
     */
    @Transactional
    fun changeStock(id: Long, amount: Int): Product {
        if (amount < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고 증가 값은 음수일 수 없습니다.")

        val product = get(id)
        if (amount > 0) product.increaseStock(amount)

        return product
    }

    @Transactional
    fun delete(id: Long) {
        val product = get(id)
        if (!product.stock.isEmpty()) {
            throw CoreException(ErrorType.CONFLICT, "재고가 남아 있어 삭제할 수 없습니다. [재고 = ${product.stock}]")
        }

        likeRepository.deleteAllByProductId(id)
        product.delete()
    }

    private fun requireExistingBrand(brandId: Long) {
        brandRepository.find(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $brandId] 브랜드를 찾을 수 없습니다.")
    }

    companion object {
        const val INITIAL_STOCK = 0
    }
}
