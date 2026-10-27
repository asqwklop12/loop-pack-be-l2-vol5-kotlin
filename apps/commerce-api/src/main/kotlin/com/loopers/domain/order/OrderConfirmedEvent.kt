package com.loopers.domain.order

data class OrderConfirmedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<Item>,
    val eventType: String = TYPE,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )

    companion object {
        const val TYPE = "OrderConfirmed"
    }
}
