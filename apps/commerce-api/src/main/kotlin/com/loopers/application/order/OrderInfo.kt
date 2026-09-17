package com.loopers.application.order

import com.loopers.domain.order.Order

data class OrderInfo(
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

    companion object {
        fun from(order: Order): OrderInfo {
            return OrderInfo(
                id = order.id,
                status = order.status.name,
                totalAmount = order.totalAmount.amount,
                paidAmount = order.paidAmount?.amount,
                items = order.items.map {
                    Item(
                        productId = it.productId,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice.amount,
                        amount = it.amount.amount,
                    )
                },
            )
        }
    }
}
