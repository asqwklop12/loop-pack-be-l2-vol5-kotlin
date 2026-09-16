package com.loopers.domain.order

enum class OrderStatus {
    /** 확정 전. 아직 재고·포인트를 차감하지 않았다. */
    DRAFT,

    /** 결제를 마친 상태. 금액이 고정된다. */
    CONFIRMED,
}
