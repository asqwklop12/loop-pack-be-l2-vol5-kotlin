package com.loopers.domain.campaign

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "campaign")
class Campaign(
    name: String,
    totalLimit: Int,
    startDate: ZonedDateTime,
    endDate: ZonedDateTime,
) : BaseEntity() {
    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "issuable", nullable = false)
    var issuable: Boolean = true
        protected set

    @Column(name = "total_limit", nullable = false)
    var totalLimit: Int = totalLimit
        protected set

    @Column(name = "issued_count", nullable = false)
    var issuedCount: Int = 0
        protected set

    @Column(name = "start_date", nullable = false)
    var startDate: ZonedDateTime = startDate
        protected set

    @Column(name = "end_date", nullable = false)
    var endDate: ZonedDateTime = endDate
        protected set

    init {
        if (totalLimit <= 0) throw CoreException(ErrorType.BAD_REQUEST, "총 발급 한도는 1 이상이어야 합니다.")
        if (!startDate.isBefore(endDate)) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 기간 시작은 끝보다 앞서야 합니다.")
        }
        if (endDate.isBefore(ZonedDateTime.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 기간 끝이 이미 지났습니다.")
        }
    }

    fun checkIssuable(now: ZonedDateTime) {
        if (issuedCount >= totalLimit) throw soldOut()
        if (now.isAfter(endDate)) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 기간이 지났습니다.")
        if (!issuable) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰을 발급할 수 없는 캠페인입니다.")
        if (now.isBefore(startDate)) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 기간이 아직 시작되지 않았습니다.")
    }

    companion object {
        fun soldOut() = CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 발급되었습니다.")
    }
}
