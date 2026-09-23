package com.loopers.domain.order

class OrderCommand {
    data class Line(
        val productId: Long,
        val quantity: Int,
    )
}
