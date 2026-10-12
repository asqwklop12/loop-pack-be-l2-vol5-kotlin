package com.loopers.domain.brand

import com.loopers.application.brand.BrandFacade
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 브랜드 삭제는 상품 전체를 건드린다. 중간에 실패하면 이번 요청의 변경이 전부 취소되어야 한다.
 * 상품마다 커밋하거나 일부만 성공으로 끝내면 안 된다.
 */
@SpringBootTest
class BrandDeleteRollbackIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val brandFacade: BrandFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @SpykBean
    private lateinit var brandRepository: BrandRepository

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun savedProduct(brandId: Long, name: String) = productJpaRepository.save(
        Product(brandId = brandId, name = name, price = Money(1_000), stock = Stock(5)),
    )

    /**
     * 실패를 **상품 중지가 끝난 뒤**에 터뜨린다. 중지 도중에 터뜨리면 전파 방식과 무관하게
     * 전부 롤백되어 깨짐을 구분하지 못한다.
     */
    @DisplayName("브랜드 삭제가 실패하면, 앞서 중지한 상품도 모두 되돌아간다.")
    @Test
    fun rollsBackSuspendedProducts_whenBrandDeleteFails() {
        // arrange
        val brand = brandService.create(name = "나이키코리아")
        val first = savedProduct(brand.id, "첫번째상품")
        val second = savedProduct(brand.id, "두번째상품")

        // 삭제 표시가 찍힌 브랜드를 저장하는 단계에서만 터뜨린다
        every { brandRepository.save(match { it.deletedAt != null }) } throws
            IllegalStateException("브랜드 삭제 실패")

        // act
        assertThrows<IllegalStateException> { brandFacade.deleteBrand(brand.id) }

        // assert
        assertAll(
            { assertThat(brandJpaRepository.findById(brand.id).get().deletedAt).isNull() },
            {
                assertThat(productJpaRepository.findById(first.id).get().status)
                    .isEqualTo(ProductStatus.SELLING)
            },
            {
                assertThat(productJpaRepository.findById(second.id).get().status)
                    .isEqualTo(ProductStatus.SELLING)
            },
        )
    }
}
