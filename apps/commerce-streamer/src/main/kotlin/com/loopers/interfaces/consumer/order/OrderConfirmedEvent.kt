package com.loopers.interfaces.consumer.order

data class OrderConfirmedEvent(
    val eventId: String = "",
    val orderId: Long = 0,
    val userId: Long = 0,
    val confirmedAt: String = "",
    val items: List<Item> = emptyList(),
    val eventType: String = "",
    val schemaVersion: Int = 0,
) {
    data class Item(
        val productId: Long = 0,
        val quantity: Int = 0,
    )
}
