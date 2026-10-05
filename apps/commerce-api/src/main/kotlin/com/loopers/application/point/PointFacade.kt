package com.loopers.application.point

import com.loopers.domain.point.PointService
import com.loopers.domain.shared.Money
import org.springframework.stereotype.Component

@Component
class PointFacade(
    private val pointService: PointService,
) {
    fun charge(userId: Long, amount: Long): PointInfo =
        PointInfo.from(pointService.charge(userId, Money(amount)))

    fun getBalance(userId: Long): PointInfo =
        PointInfo.from(pointService.getBalance(userId))
}
