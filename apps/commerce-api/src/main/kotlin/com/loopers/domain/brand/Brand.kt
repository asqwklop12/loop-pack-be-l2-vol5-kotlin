package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brand")
class Brand(
    name: String,
) : BaseEntity() {
    var name: String = name
        protected set

    init {
        guardName(name)
    }

    private fun guardName(name: String) {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 명은 공백일 수 없습니다.")
        if (name.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "브랜드명은 ${MIN_NAME_LENGTH}자리 이상 ${MAX_NAME_LENGTH}자리 이하입니다.",
            )
        }
    }

    companion object {
        const val MIN_NAME_LENGTH = 4
        const val MAX_NAME_LENGTH = 8
    }
}
