package com.loopers.domain.product

/**
 * 인기순 **상위 [CACHED_RANKS] 위의 순서만** 담는다. 상품 내용은 담지 않는다.
 *
 * `likes_desc` 는 `ORDER BY COUNT(product_like.id)` 라 정렬 키가 저장된 컬럼이 아니다.
 * 미리 정렬해 둘 대상이 없어 인덱스로 해결되지 않고, 20개를 돌려주려고 판매 중인 상품
 * 전부의 좋아요를 세고 전부 정렬한다. 그 순서를 캐시가 대신 들고 있는다.
 *
 * `page` 와 `size` 는 키에 넣지 않는다. 넣으면 사용자가 보내는 조합마다 키가 생겨
 * 개수가 끝이 없고, 그러면 두 가지가 같이 망가진다.
 *
 *  - **적중률** — 트래픽이 키로 흩어져 대부분 TTL 안에 두 번째 요청을 못 받는다.
 *  - **무효화** — 좋아요가 눌렸을 때 버릴 키를 열거할 수 없다.
 *
 * 대신 상위 [CACHED_RANKS] 개를 한 덩어리로 담고 호출자가 잘라 쓴다. 키는
 * `(정렬, 브랜드)` 로만 갈리므로 브랜드 수만큼이고, 좋아요 하나가 무효로 만드는 키는
 * **그 상품의 브랜드와 전체, 둘뿐**이다.
 *
 * 담는 것이 id 뿐이라 가격·재고·좋아요 수는 매번 DB 에서 읽는다. 순서만 조금 지난 값이 된다.
 */
interface ProductSearchCache {
    /** 캐시에 없으면 `null`. 빈 목록과 구분해야 하므로 `emptyList()` 를 쓰지 않는다. */
    fun findTop(sort: ProductSort, brandId: Long?): List<Long>?

    fun putTop(sort: ProductSort, brandId: Long?, productIds: List<Long>)

    /** 해당 `(정렬, 브랜드)` 의 캐시를 버린다. */
    fun evict(sort: ProductSort, brandId: Long?)

    companion object {
        /**
         * 담아 두는 순위의 개수. 이 범위를 넘는 요청(뒤 페이지, 더 큰 size)은 캐시를 거치지 않는다.
         *
         * 인기순의 뒤 페이지는 거의 보지 않는다. 범위를 넓히면 미스 한 번의 비용과
         * 담는 양이 같이 커지므로 기본 페이지 크기에 맞춰 둔다.
         */
        const val CACHED_RANKS = 20
    }
}
