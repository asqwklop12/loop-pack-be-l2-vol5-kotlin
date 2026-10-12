package com.loopers.domain.product

interface ProductRepository {
    fun save(product: Product): Product
    fun find(id: Long): Product?
    fun countActiveByBrandId(brandId: Long): Long
    fun findAllActiveByBrandId(brandId: Long): List<Product>

    /**
     * 그 브랜드의 판매 중인 상품을 **한 번에** 판매중지로 바꾼다. 바뀐 개수를 돌려준다.
     * 상품 수만큼 UPDATE 가 도는 것을 피한다.
     */
    fun suspendAllByBrandId(brandId: Long): Int
    fun findAll(): List<Product>
    fun findAllByIds(ids: List<Long>): List<Product>
    fun search(search: ProductSearch): List<Product>
    fun countBy(brandId: Long?): Long
}
