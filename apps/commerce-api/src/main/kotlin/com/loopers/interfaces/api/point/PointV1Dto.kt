package com.loopers.interfaces.api.point

class PointV1Dto {
    data class ChargeRequest(val amount: Long)

    data class BalanceResponse(val balance: Long)
}
