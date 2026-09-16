package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.point.PointService
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
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val pointService: PointService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun savedProduct(price: Long = 1_000, stock: Int = 5): Product {
        val brand = brandJpaRepository.save(Brand(name = "브랜드${System.nanoTime()}"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(price), stock = Stock(stock)),
        )
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("없는 상품이 있으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductDoesNotExist() {
            val result = assertThrows<CoreException> {
                orderService.create(userId, listOf(OrderCommand.Line(productId = -1L, quantity = 1)))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품이 있으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenProductIsDeleted() {
            val product = savedProduct()
            productJpaRepository.save(product.apply { delete() })

            val result = assertThrows<CoreException> {
                orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 1)))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("단가는 주문 시점의 상품 가격으로 저장되고, 재고는 줄지 않는다.")
        @Test
        fun storesUnitPrice_andDoesNotDecreaseStock() {
            val product = savedProduct(price = 1_000, stock = 5)

            val order = orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 2)))

            assertAll(
                { assertThat(order.status).isEqualTo(OrderStatus.DRAFT) },
                { assertThat(order.totalAmount).isEqualTo(Money(2_000)) },
                { assertThat(order.items.first().unitPrice).isEqualTo(Money(1_000)) },
                { assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(5)) },
            )
        }
    }

    @DisplayName("주문을 확정할 때, ")
    @Nested
    inner class Confirm {
        @DisplayName("남의 주문이면, 없는 주문과 같은 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenNotOwner() {
            val product = savedProduct()
            val order = orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 1)))

            val result = assertThrows<CoreException> { orderService.confirm(userId = 999L, orderId = order.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생하고 포인트가 줄지 않는다.")
        @Test
        fun throwsBadRequestException_whenStockIsNotEnough() {
            val product = savedProduct(price = 1_000, stock = 1)
            val order = orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 1)))
            pointService.charge(userId, Money(10_000))
            productJpaRepository.save(product.apply { changeStock(0) })

            val result = assertThrows<CoreException> { orderService.confirm(userId, order.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(pointService.getBalance(userId)).isEqualTo(Money(10_000))
        }

        @DisplayName("잔액이 부족하면, BAD_REQUEST 예외가 발생하고 재고가 복원된다.")
        @Test
        fun throwsBadRequestException_whenBalanceIsNotEnough() {
            val product = savedProduct(price = 10_000, stock = 5)
            val order = orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 1)))
            pointService.charge(userId, Money(1_000))

            val result = assertThrows<CoreException> { orderService.confirm(userId, order.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(5))
        }

        @DisplayName("확정하면, 재고와 잔액이 줄고 결제액이 저장된다.")
        @Test
        fun decreasesStockAndBalance_andStoresPaidAmount() {
            val product = savedProduct(price = 7_000, stock = 5)
            val order = orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 1)))
            pointService.charge(userId, Money(10_000))

            val confirmed = orderService.confirm(userId, order.id)

            assertAll(
                { assertThat(confirmed.status).isEqualTo(OrderStatus.CONFIRMED) },
                { assertThat(confirmed.paidAmount).isEqualTo(Money(7_000)) },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(3_000)) },
                { assertThat(productJpaRepository.findById(product.id).get().stock).isEqualTo(Stock(4)) },
            )
        }

        @DisplayName("이미 확정된 주문을 다시 확정하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflictException_whenAlreadyConfirmed() {
            val product = savedProduct(price = 1_000, stock = 5)
            val order = orderService.create(userId, listOf(OrderCommand.Line(productId = product.id, quantity = 1)))
            pointService.charge(userId, Money(10_000))
            orderService.confirm(userId, order.id)

            val result = assertThrows<CoreException> { orderService.confirm(userId, order.id) }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
