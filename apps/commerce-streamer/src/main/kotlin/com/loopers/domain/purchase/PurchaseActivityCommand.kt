package com.loopers.domain.purchase

data class PurchaseActivityCommand(
    val orderId: Long,
    val userId: Long,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
