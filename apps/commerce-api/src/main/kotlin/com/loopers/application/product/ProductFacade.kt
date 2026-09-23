package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSearch
import com.loopers.domain.product.ProductService
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 상품·브랜드·좋아요 수를 조합한다. 조회 결과의 구성은 application 의 책임이다.
 *
 * 트랜잭션 경계는 이 계층이다. 도메인 서비스마다 따로 열리면 목록 하나에 트랜잭션이
 * 상품 수만큼 늘어난다.
 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
) {
    @Transactional(readOnly = true)
    fun getProduct(productId: Long): ProductInfo = combine(listOf(productService.get(productId))).first()

    @Transactional(readOnly = true)
    fun getProducts(): List<ProductInfo> = combine(productService.getAll())

    @Transactional(readOnly = true)
    fun searchProducts(search: ProductSearch): ProductPage {
        return ProductPage(
            items = combine(productService.search(search)),
            page = search.page,
            size = search.size,
            totalCount = productService.countBy(search.brandId),
        )
    }

    /**
     * 내가 좋아요한 상품 목록. 삭제된 상품은 제외한다.
     */
    @Transactional(readOnly = true)
    fun getLikedProducts(userId: Long): List<ProductInfo> =
        combine(productService.getAllByIds(likeService.likedProductIds(userId)))

    @Transactional
    fun createProduct(brandId: Long, name: String, price: Long): ProductInfo =
        combine(listOf(productService.create(brandId = brandId, name = name, price = Money(price)))).first()

    @Transactional
    fun updateProduct(productId: Long, name: String, price: Long): ProductInfo =
        combine(listOf(productService.update(id = productId, name = name, price = Money(price)))).first()

    @Transactional
    fun changeStock(productId: Long, amount: Int): ProductInfo =
        combine(listOf(productService.changeStock(id = productId, amount = amount))).first()

    @Transactional
    fun deleteProduct(productId: Long) = productService.delete(productId)

    /**
     * 브랜드와 좋아요 수를 한 번씩만 조회해 붙인다. 상품마다 조회하면 목록 크기만큼 쿼리가 늘어난다.
     */
    private fun combine(products: List<Product>): List<ProductInfo> {
        if (products.isEmpty()) return emptyList()

        val brands = brandService.getAllByIds(products.map { it.brandId }.distinct())
            .associateBy { it.id }
        val likeCounts = likeService.countsOf(products.map { it.id })

        return products.map { product ->
            ProductInfo.of(
                product = product,
                brand = brandOf(brands, product),
                likeCount = likeCounts[product.id] ?: 0L,
            )
        }
    }

    private fun brandOf(brands: Map<Long, Brand>, product: Product): Brand {
        return brands[product.brandId]
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = ${product.brandId}] 브랜드를 찾을 수 없습니다.")
    }
}
