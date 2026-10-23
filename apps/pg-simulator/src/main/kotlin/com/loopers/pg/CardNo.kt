package com.loopers.pg

object CardNo {
    fun mask(cardNo: String): String {
        val digits = cardNo.filter { it.isDigit() }

        return if (digits.length <= 4) "****" else "****-****-****-" + digits.takeLast(4)
    }
}
