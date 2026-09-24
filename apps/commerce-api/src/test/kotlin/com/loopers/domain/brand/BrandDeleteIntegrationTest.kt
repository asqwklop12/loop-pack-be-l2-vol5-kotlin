package com.loopers.domain.brand

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductStatus
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
import org.junit.jupiter.api.assertAll
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

    private fun savedProduct(brandId: Long, name: String = "에어포스1", stock: Int = 0) =
        productJpaRepository.save(
            Product(brandId = brandId, name = name, price = Money(129_000), stock = Stock(stock)),
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

        @DisplayName("연결된 상품이 없으면, 삭제 시점이 기록된다.")
        @Test
        fun marksDeletedAt_whenNoProduct() {
            val brand = brandService.create(name = "나이키코리아")

            brandService.delete(brand.id)

            assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNotNull()
        }

        @DisplayName("상품이 남아 있어도 삭제되며, 그 상품은 판매중지가 된다.")
        @Test
        fun suspendsProducts_andDeletesBrand() {
            val brand = brandService.create(name = "나이키코리아")
            val product = savedProduct(brand.id, stock = 3)

            brandService.delete(brand.id)

            assertAll(
                { assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNotNull() },
                { assertThat(productJpaRepository.findById(product.id).get().status).isEqualTo(ProductStatus.SUSPENDED) },
            )
        }

        @DisplayName("그 브랜드의 상품 여러 개가 모두 판매중지가 된다.")
        @Test
        fun suspendsAllProductsOfTheBrand() {
            val brand = brandService.create(name = "나이키코리아")
            val first = savedProduct(brand.id, name = "에어포스1")
            val second = savedProduct(brand.id, name = "에어맥스")

            brandService.delete(brand.id)

            assertAll(
                { assertThat(productJpaRepository.findById(first.id).get().status).isEqualTo(ProductStatus.SUSPENDED) },
                { assertThat(productJpaRepository.findById(second.id).get().status).isEqualTo(ProductStatus.SUSPENDED) },
            )
        }

        @DisplayName("다른 브랜드의 상품은 판매 중으로 남는다.")
        @Test
        fun keepsOtherBrandProductsSelling() {
            val nike = brandService.create(name = "나이키코리아")
            val adidas = brandService.create(name = "아디다스코리아")
            savedProduct(nike.id, name = "에어포스1")
            val other = savedProduct(adidas.id, name = "슈퍼스타")

            brandService.delete(nike.id)

            assertThat(productJpaRepository.findById(other.id).get().status).isEqualTo(ProductStatus.SELLING)
        }
    }
}
