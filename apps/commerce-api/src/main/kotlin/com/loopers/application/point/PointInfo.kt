package com.loopers.application.point

import com.loopers.domain.shared.Money

data class PointInfo(
    val balance: Long,
) {
    companion object {
        fun from(balance: Money): PointInfo = PointInfo(balance = balance.amount)
    }
}
