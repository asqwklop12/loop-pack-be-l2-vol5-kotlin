package com.loopers.domain.order

enum class OrderStatus {
    /** 확정 전. 재고는 잡혀 있고 결제는 아직이다. */
    DRAFT,

    /** 결제를 마친 상태. 금액이 고정된다. */
    CONFIRMED,

    /** 취소됨. 주문 기록은 남기고 상태만 바꾼다. */
    CANCELED,
}
