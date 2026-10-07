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

    /**
     * 고객이 쓰는 모든 경로의 기준이다. 판매중지 상품은 **없는 것과 같은 응답**을 준다.
     * 중지됐다는 사실이 새어나가지 않아야 고객의 사용 대상에서 제외된 것이다.
     * 관리자 조회는 [get] 을 쓴다.
     */
    @Transactional(readOnly = true)
    fun getSellable(id: Long): Product {
        return get(id).takeIf { it.isSellable() }
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 상품을 찾을 수 없습니다.")
    }

    @Transactional(readOnly = true)
    fun getAll(): List<Product> = productRepository.findAll()

    /**
     * 브랜드가 내려갈 때 그 브랜드의 상품을 모두 판매중지로 바꾼다.
     */
    @Transactional
    fun suspendAllByBrand(brandId: Long) {
        productRepository.findAllActiveByBrandId(brandId)
            .forEach { productRepository.save(it.also { product -> product.suspend() }) }
    }

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

    /**
     * 판매중지 상품은 바꿀 수 없다. 고객에게 안 보이는 채로 상품이 움직이면 안 되고,
     * 되살릴 길도 없으므로 없는 상품과 같이 취급한다.
     */
    @Transactional
    fun update(id: Long, name: String, price: Money): Product {
        val product = getSellable(id).also { it.update(name = name, price = price) }

        return productRepository.save(product)
    }

    /**
     * 최종 수량을 설정하지 않고 증가 값을 더한다. 음수만 거절하므로 0 은 재고를 바꾸지 않는다.
     * 판매중지 상품은 [update] 와 같은 이유로 거절한다.
     */
    @Transactional
    fun changeStock(id: Long, amount: Int): Product {
        if (amount < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고 증가 값은 음수일 수 없습니다.")

        val product = getSellable(id)
        if (amount > 0) product.increaseStock(amount)

        return productRepository.save(product)
    }

    /**
     * 주문이 재고를 잡을 때 쓴다. 규칙은 [Product.decreaseStock] 이 지킨다.
     */
    @Transactional
    fun decreaseStock(product: Product, quantity: Int): Product {
        product.decreaseStock(quantity)

        return productRepository.save(product)
    }

    /**
     * 주문 취소가 재고를 되돌릴 때 쓴다.
     */
    @Transactional
    fun restoreStock(productId: Long, quantity: Int): Product {
        val product = get(productId).also { it.increaseStock(quantity) }

        return productRepository.save(product)
    }

    @Transactional
    fun delete(id: Long) {
        val product = get(id)
        if (!product.stock.isEmpty()) {
            throw CoreException(ErrorType.CONFLICT, "재고가 남아 있어 삭제할 수 없습니다. [재고 = ${product.stock}]")
        }

        likeRepository.deleteAllByProductId(id)
        product.delete()
        productRepository.save(product)
    }

    private fun requireExistingBrand(brandId: Long) {
        brandRepository.find(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $brandId] 브랜드를 찾을 수 없습니다.")
    }

    companion object {
        const val INITIAL_STOCK = 0
    }
}
