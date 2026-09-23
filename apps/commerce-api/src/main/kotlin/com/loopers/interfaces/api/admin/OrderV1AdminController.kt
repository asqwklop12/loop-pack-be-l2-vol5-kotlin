package com.loopers.interfaces.api.admin

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderInfo
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/orders")
class OrderV1AdminController(
    private val orderFacade: OrderFacade,
) {
    @GetMapping
    fun getOrders(
        @RequestHeader(value = "X-USER-ROLE") role: String,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>> {
        AdminRole.guard(role)

        return orderFacade.getAllOrders()
            .map { toResponse(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @RequestHeader(value = "X-USER-ROLE") role: String,
        @PathVariable(value = "orderId") orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        AdminRole.guard(role)

        return orderFacade.getAnyOrder(orderId)
            .let { toResponse(it) }
            .let { ApiResponse.success(it) }
    }

    private fun toResponse(info: OrderInfo) = OrderV1Dto.OrderResponse(
        id = info.id,
        status = info.status,
        totalAmount = info.totalAmount,
        paidAmount = info.paidAmount,
        lines = info.lines.map {
            OrderV1Dto.OrderResponse.Item(
                productId = it.productId,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                amount = it.amount,
            )
        },
    )
}
