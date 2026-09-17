package com.loopers.domain.brand

import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
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
class BrandDeleteIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun savedProduct(brandId: Long, stock: Int = 0) = productJpaRepository.save(
        Product(brandId = brandId, name = "에어포스1", price = Money(129_000), stock = Stock(stock)),
    )

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("존재하지 않는 브랜드면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandDoesNotExist() {
            val result = assertThrows<CoreException> { brandService.delete(-1L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제되지 않은 상품이 남아 있으면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenActiveProductRemains() {
            val brand = brandService.create(name = "나이키코리아")
            savedProduct(brand.id)

            val result = assertThrows<CoreException> { brandService.delete(brand.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNull()
        }

        @DisplayName("재고가 0이 아니어도, 상품이 남아 있으면 거절한다.")
        @Test
        fun throwsConflictException_evenWhenStockRemains() {
            val brand = brandService.create(name = "나이키코리아")
            savedProduct(brand.id, stock = 3)

            val result = assertThrows<CoreException> { brandService.delete(brand.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("상품이 모두 삭제됐으면, 브랜드를 삭제할 수 있다.")
        @Test
        fun deletesBrand_whenAllProductsAreDeleted() {
            val brand = brandService.create(name = "나이키코리아")
            val product = savedProduct(brand.id, stock = 3)
            productJpaRepository.save(product.apply { delete() })

            brandService.delete(brand.id)

            assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNotNull()
        }

        @DisplayName("연결된 상품이 없으면, 삭제 시점이 기록된다.")
        @Test
        fun marksDeletedAt_whenNoProduct() {
            val brand = brandService.create(name = "나이키코리아")

            brandService.delete(brand.id)

            assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNotNull()
        }
    }
}
