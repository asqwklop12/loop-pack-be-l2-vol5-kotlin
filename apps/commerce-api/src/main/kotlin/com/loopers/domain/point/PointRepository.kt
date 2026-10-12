package com.loopers.domain.point

interface PointRepository {
    fun save(pointBalance: PointBalance): PointBalance
    fun findByUserId(userId: Long): PointBalance?

    /**
     * 잔액을 바꿀 때 쓴다. 읽은 값이 그대로일 거라 믿지 않고 **행을 잠근 뒤 읽는다.**
     * 충전과 결제가 같은 행을 다투지만 둘 다 성립하는 요청이라, 떨어뜨리지 않고 줄을 세운다.
     */
    fun findByUserIdForUpdate(userId: Long): PointBalance?
}
