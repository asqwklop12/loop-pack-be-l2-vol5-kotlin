package com.loopers.pg

enum class DeclineReason(val message: String) {
    LIMIT_EXCEEDED("한도초과"),
    INSUFFICIENT_BALANCE("잔액부족"),
    LOST_OR_STOLEN("분실·도난 카드"),
    ISSUER_UNAVAILABLE("카드사 응답 없음"),
}
