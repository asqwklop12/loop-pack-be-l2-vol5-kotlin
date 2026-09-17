package com.loopers.domain.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
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
class LikeServiceIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val likeJpaRepository: LikeJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedProduct(): Product {
        val brand = brandJpaRepository.save(Brand(name = "나이키코리아"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "에어포스1", price = Money(129_000), stock = Stock(5)),
        )
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    inner class Like {
        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductDoesNotExist() {
            val result = assertThrows<CoreException> { likeService.like(userId = userId, productId = -1L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductIsDeleted() {
            val product = savedProduct()
            productJpaRepository.save(product.apply { delete() })

            val result = assertThrows<CoreException> { likeService.like(userId = userId, productId = product.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("처음 누르면, 관계가 저장된다.")
        @Test
        fun savesRelation_whenFirstTime() {
            val product = savedProduct()

            likeService.like(userId = userId, productId = product.id)

            assertThat(likeJpaRepository.findByUserIdAndProductId(userId, product.id)).isNotNull()
        }

        @DisplayName("이미 눌렀어도 성공하며, 관계는 하나만 저장된다.")
        @Test
        fun keepsSingleRelation_whenAlreadyLiked() {
            val product = savedProduct()
            likeService.like(userId = userId, productId = product.id)

            likeService.like(userId = userId, productId = product.id)

            assertThat(likeJpaRepository.countByProductId(product.id)).isEqualTo(1L)
        }
    }

    @DisplayName("좋아요를 해제할 때, ")
    @Nested
    inner class Unlike {
        @DisplayName("누른 적이 있으면, 관계가 지워진다.")
        @Test
        fun removesRelation_whenLiked() {
            val product = savedProduct()
            likeService.like(userId = userId, productId = product.id)

            likeService.unlike(userId = userId, productId = product.id)

            assertThat(likeJpaRepository.findByUserIdAndProductId(userId, product.id)).isNull()
        }

        @DisplayName("누른 적이 없어도, 성공으로 끝난다.")
        @Test
        fun succeeds_whenNotLiked() {
            val product = savedProduct()

            likeService.unlike(userId = userId, productId = product.id)

            assertThat(likeJpaRepository.findByUserIdAndProductId(userId, product.id)).isNull()
        }

        @DisplayName("상품이 삭제됐어도, 남아 있는 자신의 관계는 취소할 수 있다.")
        @Test
        fun removesRelation_whenProductIsDeleted() {
            val product = savedProduct()
            likeService.like(userId = userId, productId = product.id)
            productJpaRepository.save(product.apply { delete() })

            likeService.unlike(userId = userId, productId = product.id)

            assertThat(likeJpaRepository.findByUserIdAndProductId(userId, product.id)).isNull()
        }

        @DisplayName("다른 사용자의 관계는 지우지 않는다.")
        @Test
        fun doesNotRemoveOthersRelation() {
            val product = savedProduct()
            likeService.like(userId = 1L, productId = product.id)
            likeService.like(userId = 2L, productId = product.id)

            likeService.unlike(userId = 1L, productId = product.id)

            assertThat(likeJpaRepository.findByUserIdAndProductId(2L, product.id)).isNotNull()
        }
    }

    @DisplayName("좋아요 수를 조회할 때, ")
    @Nested
    inner class CountOf {
        @DisplayName("서로 다른 사용자가 누른 만큼 집계된다.")
        @Test
        fun countsEachUserOnce() {
            val product = savedProduct()
            likeService.like(userId = 1L, productId = product.id)
            likeService.like(userId = 2L, productId = product.id)
            likeService.like(userId = 2L, productId = product.id)

            assertThat(likeService.countOf(product.id)).isEqualTo(2L)
        }
    }
}
