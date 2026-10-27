package com.loopers.interfaces.consumer.order

data class OrderConfirmedEvent(
    val orderId: Long = 0,
    val userId: Long = 0,
    val items: List<Item> = emptyList(),
    val eventType: String = "",
) {
    data class Item(
        val productId: Long = 0,
        val quantity: Int = 0,
    )
}
