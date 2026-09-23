package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class ProductSearch(
    val brandId: Long? = null,
    val sort: ProductSort = ProductSort.DEFAULT,
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        if (page < 0) throw CoreException(ErrorType.BAD_REQUEST, "페이지는 0 이상이어야 합니다.")
        if (size !in 1..MAX_SIZE) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상 ${MAX_SIZE} 이하여야 합니다.")
        }
    }

    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}
