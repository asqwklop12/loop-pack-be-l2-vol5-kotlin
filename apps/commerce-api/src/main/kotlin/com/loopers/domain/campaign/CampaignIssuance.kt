package com.loopers.domain.campaign

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

data class CampaignIssuance(
    val issuable: Boolean,
    val startDate: ZonedDateTime,
    val endDate: ZonedDateTime,
) {
    fun check(now: ZonedDateTime) {
        if (now.isAfter(endDate)) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 기간이 지났습니다.")
        if (!issuable) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰을 발급할 수 없는 캠페인입니다.")
        if (now.isBefore(startDate)) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 기간이 아직 시작되지 않았습니다.")
    }
}
