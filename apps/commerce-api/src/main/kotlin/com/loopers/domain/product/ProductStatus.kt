package com.loopers.domain.product

enum class ProductStatus {
    /** 판매 중. 목록에 나오고 주문할 수 있다. */
    SELLING,

    /** 판매 중지. 상세는 보이지만 주문할 수 없다. */
    SUSPENDED,
}
