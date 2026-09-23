package com.loopers.application.product

data class ProductPage(
    val items: List<ProductInfo>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)
