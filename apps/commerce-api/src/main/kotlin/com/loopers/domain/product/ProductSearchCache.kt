package com.loopers.domain.product

/**
 * 인기순 목록의 **정렬 결과만** 담는다. 상품 내용은 담지 않는다.
 *
 * `likes_desc` 는 `ORDER BY COUNT(product_like.id)` 라 정렬 키가 저장된 컬럼이 아니다.
 * 미리 정렬해 둘 대상이 없어 인덱스로 해결되지 않고, 20개를 돌려주려고 판매 중인 상품
 * 전부의 좋아요를 세고 전부 정렬한다. 그 순서를 캐시가 대신 들고 있는다.
 *
 * 담는 것이 id 뿐이라 가격·재고·좋아요 수는 매번 DB 에서 읽는다. 순서만 조금 지난 값이 된다.
 */
interface ProductSearchCache {
    /** 캐시에 없으면 `null`. 빈 목록과 구분해야 하므로 `emptyList()` 를 쓰지 않는다. */
    fun find(search: ProductSearch): List<Long>?

    fun put(search: ProductSearch, productIds: List<Long>)

    /** 해당 조건의 캐시를 버린다. */
    fun evict(search: ProductSearch)
}
