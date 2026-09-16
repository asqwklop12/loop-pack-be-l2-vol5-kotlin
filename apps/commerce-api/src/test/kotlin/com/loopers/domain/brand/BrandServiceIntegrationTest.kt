package com.loopers.domain.brand

import com.loopers.infrastructure.brand.BrandJpaRepository
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
class BrandServiceIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    inner class Create {
        @DisplayName("같은 이름의 브랜드가 이미 있으면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenNameAlreadyExists() {
            // arrange
            brandService.create(name = "나이키")

            // act
            val result = assertThrows<CoreException> { brandService.create(name = "나이키") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    inner class Get {
        @DisplayName("존재하지 않는 브랜드 ID 를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandDoesNotExist() {
            // act
            val result = assertThrows<CoreException> { brandService.get(id = -1L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드 ID 를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenBrandIsDeleted() {
            // arrange
            val saved = brandService.create(name = "나이키")
            brandJpaRepository.save(saved.apply { delete() })

            // act
            val result = assertThrows<CoreException> { brandService.get(id = saved.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
