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

/**
 * 상품·브랜드·좋아요 수를 조합한다. 조회 결과의 구성은 application 의 책임이다.

 */
@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
) {
    /**
     * 고객 상세 조회. 판매중지 상품은 없는 것과 같은 응답을 준다.
     */
    fun getProduct(productId: Long): ProductInfo = combine(listOf(productService.getSellable(productId))).first()

    /**
     * 관리자 상세 조회. 판매중지 상품도 보인다.
     */
    fun getProductForAdmin(productId: Long): ProductInfo = combine(listOf(productService.get(productId))).first()

    fun getProducts(): List<ProductInfo> = combine(productService.getAll())

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
    fun getLikedProducts(userId: Long): List<ProductInfo> =
        combine(productService.getAllByIds(likeService.likedProductIds(userId)))

    fun createProduct(brandId: Long, name: String, price: Long): ProductInfo =
        combine(listOf(productService.create(brandId = brandId, name = name, price = Money(price)))).first()

    fun updateProduct(productId: Long, name: String, price: Long): ProductInfo =
        combine(listOf(productService.update(id = productId, name = name, price = Money(price)))).first()

    fun changeStock(productId: Long, amount: Int): ProductInfo =
        combine(listOf(productService.changeStock(id = productId, amount = amount))).first()

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
