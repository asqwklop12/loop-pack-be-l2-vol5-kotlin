package com.loopers.domain.product

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductTest {
    private fun product(
        brandId: Long = 1L,
        name: String = "에어포스1",
        price: Money = Money(129_000),
        stock: Stock = Stock(5),
    ) = Product(brandId = brandId, name = name, price = price, stock = stock)

    @DisplayName("상품을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsProduct_whenValuesAreValid() {
            val product = product()

            assertAll(
                { assertThat(product.brandId).isEqualTo(1L) },
                { assertThat(product.name).isEqualTo("에어포스1") },
                { assertThat(product.price).isEqualTo(Money(129_000)) },
                { assertThat(product.stock).isEqualTo(Stock(5)) },
                { assertThat(product.deletedAt).isNull() },
            )
        }

        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNameIsBlank() {
            val result = assertThrows<CoreException> { product(name = "   ") }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 2자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNameIsTooShort() {
            val result = assertThrows<CoreException> { product(name = "가") }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 20자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNameIsTooLong() {
            val result = assertThrows<CoreException> { product(name = "가".repeat(21)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 2자면, 정상적으로 생성된다.")
        @Test
        fun createsProduct_whenNameIsExactlyMinLength() {
            val product = product(name = "가나")

            assertThat(product.name).isEqualTo("가나")
        }

        @DisplayName("이름이 20자면, 정상적으로 생성된다.")
        @Test
        fun createsProduct_whenNameIsExactlyMaxLength() {
            val name = "가".repeat(20)

            val product = product(name = name)

            assertThat(product.name).isEqualTo(name)
        }

        @DisplayName("가격이 0원이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPriceIsZero() {
            val result = assertThrows<CoreException> { product(price = Money.ZERO) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    inner class Update {
        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생하고 기존 값이 유지된다.")
        @Test
        fun throwsBadRequestException_whenNewNameIsBlank() {
            val product = product()

            val result = assertThrows<CoreException> { product.update(name = "   ", price = Money(1_000)) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(product.name).isEqualTo("에어포스1")
            assertThat(product.price).isEqualTo(Money(129_000))
        }

        @DisplayName("유효한 값이 주어지면, 이름과 가격이 바뀐다.")
        @Test
        fun updatesNameAndPrice_whenValuesAreValid() {
            val product = product()

            product.update(name = "에어포스2", price = Money(139_000))

            assertAll(
                { assertThat(product.name).isEqualTo("에어포스2") },
                { assertThat(product.price).isEqualTo(Money(139_000)) },
                { assertThat(product.brandId).isEqualTo(1L) },
            )
        }
    }

    @DisplayName("재고를 변경할 때, ")
    @Nested
    inner class ChangeStock {
        @DisplayName("최종 수량이 음수면, BAD_REQUEST 예외가 발생하고 기존 재고가 유지된다.")
        @Test
        fun throwsBadRequestException_whenQuantityIsNegative() {
            val product = product()

            val result = assertThrows<CoreException> { product.changeStock(-1) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(product.stock).isEqualTo(Stock(5))
        }

        @DisplayName("최종 수량이 0이면, 재고가 0이 된다.")
        @Test
        fun setsStockToZero_whenQuantityIsZero() {
            val product = product()

            product.changeStock(0)

            assertThat(product.stock).isEqualTo(Stock(0))
        }
    }
}
