package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 동률은 모두 `id` 내림차순으로 가른다. 겹치지 않는 값이 없으면 페이지마다 순서가 달라져
 * 같은 상품이 두 번 나오거나 빠질 수 있다.
 */
enum class ProductSort(val value: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        val DEFAULT = LATEST

        fun from(value: String?): ProductSort {
            if (value == null) return DEFAULT

            return entries.firstOrNull { it.value == value }
                ?: throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "정렬 값이 잘못되었습니다. 사용 가능한 값 : [${entries.joinToString(", ") { it.value }}]",
                )
        }
    }
}
