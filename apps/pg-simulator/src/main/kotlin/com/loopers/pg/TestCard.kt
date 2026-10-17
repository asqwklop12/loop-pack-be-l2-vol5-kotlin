package com.loopers.pg

enum class TestCard(val suffix: String) {
    DECLINED("0000"),
    SLOW("1111"),
    SILENT("2222"),
    ;

    companion object {
        fun of(cardNo: String): TestCard? {
            val digits = cardNo.filter { it.isDigit() }

            return entries.firstOrNull { digits.endsWith(it.suffix) }
        }
    }
}
