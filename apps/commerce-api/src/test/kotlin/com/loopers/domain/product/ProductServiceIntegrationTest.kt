package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductServiceIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedBrand(name: String = "나이키") = brandJpaRepository.save(Brand(name = name))

    @DisplayName("상품을 등록할 때, ")
    @Nested
    inner class Create {
        @DisplayName("존재하지 않는 브랜드를 참조하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandDoesNotExist() {
            val result = assertThrows<CoreException> {
                productService.create(brandId = -1L, name = "에어포스1", price = Money(129_000), stock = Stock(5))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드를 참조하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandIsDeleted() {
            val brand = savedBrand()
            brandJpaRepository.save(brand.apply { delete() })

            val result = assertThrows<CoreException> {
                productService.create(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(5))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("유효한 브랜드를 참조하면, 상품이 저장된다.")
        @Test
        fun savesProduct_whenBrandIsValid() {
            val brand = savedBrand()

            val product =
                productService.create(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(5))

            assertThat(productJpaRepository.findByIdAndDeletedAtIsNull(product.id)).isNotNull()
        }
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    inner class Get {
        @DisplayName("존재하지 않는 상품 ID 를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductDoesNotExist() {
            val result = assertThrows<CoreException> { productService.get(id = -1L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품 ID 를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductIsDeleted() {
            val brand = savedBrand()
            val product =
                productService.create(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(0))
            productService.delete(product.id)

            val result = assertThrows<CoreException> { productService.get(id = product.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("재고가 남아 있으면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenStockRemains() {
            val brand = savedBrand()
            val product =
                productService.create(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(1))

            val result = assertThrows<CoreException> { productService.delete(product.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("재고가 0이면, 삭제 시점이 기록된다.")
        @Test
        fun marksDeletedAt_whenStockIsEmpty() {
            val brand = savedBrand()
            val product =
                productService.create(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(0))

            productService.delete(product.id)

            assertThat(productJpaRepository.findById(product.id).get().deletedAt).isNotNull()
        }
    }
}
