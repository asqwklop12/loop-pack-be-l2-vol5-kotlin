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
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.")
        if (name.length > MAX_NAME_LENGTH) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 ${MAX_NAME_LENGTH}자를 넘을 수 없습니다.")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 50
    }
}
