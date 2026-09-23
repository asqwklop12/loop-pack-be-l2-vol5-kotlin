package com.loopers.domain.point

import com.loopers.domain.shared.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointService(
    private val pointRepository: PointRepository,
) {
    @Transactional
    fun charge(userId: Long, amount: Money): Money {
        val point = pointRepository.findByUserId(userId)
            ?: PointBalance(userId = userId)
        point.charge(amount)

        return pointRepository.save(point).balance
    }

    @Transactional
    fun pay(userId: Long, amount: Money): Money {
        val point = pointRepository.findByUserId(userId)
            ?: PointBalance(userId = userId)
        point.pay(amount)

        return pointRepository.save(point).balance
    }

    /**
     * 주문 취소로 결제액을 되돌린다. 잔액이 늘어난다는 점은 충전과 같지만 사유가 다르다.
     */
    @Transactional
    fun refund(userId: Long, amount: Money): Money = charge(userId, amount)

    /**
     * 충전한 적이 없는 사용자의 잔액은 0원이다. 잔액 0원은 유효한 상태다.
     */
    @Transactional(readOnly = true)
    fun getBalance(userId: Long): Money {
        return pointRepository.findByUserId(userId)?.balance ?: Money.ZERO
    }
}
