package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class BrandTest {
    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 이름이 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsBrand_whenNameIsValid() {
            // arrange
            val name = "나이키"

            // act
            val brand = Brand(name = name)

            // assert
            assertAll(
                { assertThat(brand.name).isEqualTo(name) },
                { assertThat(brand.deletedAt).isNull() },
            )
        }

        @DisplayName("이름이 50자면, 정상적으로 생성된다.")
        @Test
        fun createsBrand_whenNameIsExactlyMaxLength() {
            // arrange
            val name = "가".repeat(50)

            // act
            val brand = Brand(name = name)

            // assert
            assertThat(brand.name).isEqualTo(name)
        }

        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNameIsBlank() {
            // arrange
            val name = "   "

            // act
            val result = assertThrows<CoreException> { Brand(name = name) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 50자를 넘으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNameIsTooLong() {
            // arrange
            val name = "가".repeat(51)

            // act
            val result = assertThrows<CoreException> { Brand(name = name) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
