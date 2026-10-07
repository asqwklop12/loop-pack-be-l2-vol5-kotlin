package com.loopers.domain.point

import com.loopers.domain.shared.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointService(
    private val pointRepository: PointRepository,
) {
    /**
     * 충전과 결제는 같은 잔액 행을 다툰다. 둘 다 성립하는 요청이므로 거절하지 않고
     * 줄을 세운다. 조회는 [getBalance] 처럼 잠그지 않는다.
     */
    @Transactional
    fun charge(userId: Long, amount: Money): Money {
        val point = pointRepository.findByUserIdForUpdate(userId)
            ?: PointBalance(userId = userId)
        point.charge(amount)

        return pointRepository.save(point).balance
    }

    @Transactional
    fun pay(userId: Long, amount: Money): Money {
        val point = pointRepository.findByUserIdForUpdate(userId)
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
