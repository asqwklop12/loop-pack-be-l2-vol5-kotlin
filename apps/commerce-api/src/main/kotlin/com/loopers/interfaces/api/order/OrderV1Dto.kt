package com.loopers.interfaces.api.order

class OrderV1Dto {
    data class CreateRequest(
        val items: List<Line>,
    ) {
        data class Line(
            val productId: Long,
            val quantity: Int,
        )
    }

    data class OrderResponse(
        val id: Long,
        val status: String,
        val totalAmount: Long,
        val paidAmount: Long?,
        val items: List<Item>,
    ) {
        data class Item(
            val productId: Long,
            val quantity: Int,
            val unitPrice: Long,
            val amount: Long,
        )
    }
}
