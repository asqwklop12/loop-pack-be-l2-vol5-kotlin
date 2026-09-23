package com.loopers.interfaces.api.admin

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 로컬 실습용 모의 역할 확인. `X-USER-ROLE` 이 ADMIN 이 아니면 거절한다.
 * 헤더 자체가 없는 것은 400 이고, 역할이 다른 것은 403 이다.
 */
object AdminRole {
    private const val ADMIN = "ADMIN"

    fun guard(role: String) {
        if (role != ADMIN) {
            throw CoreException(ErrorType.FORBIDDEN, "관리자만 사용할 수 있습니다.")
        }
    }
}
