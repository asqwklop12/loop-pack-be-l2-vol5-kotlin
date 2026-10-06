package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 확정은 재고와 포인트를 함께 움직인다. 같은 행을 동시에 건드리면 읽기와 쓰기 사이에
 * 다른 트랜잭션이 끼어들어 **장부가 어긋난다.**
 *
 * 세 자리를 각각 드러낸다. 막는 자리가 달라서 한 번에 묶이지 않는다.
 * - 같은 주문을 두 번 확정 — 주문 행
 * - 한 상품을 여러 주문이 확정 — 상품 행
 * - 한 사용자가 여러 주문을 확정 — 포인트 행
 */
@SpringBootTest
class OrderConfirmConcurrencyIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val pointService: PointService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun savedProduct(price: Long = 1_000, stock: Int = 1): Product {
        val brand = brandJpaRepository.save(Brand(name = "테스트브랜드"))
        return productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(price), stock = Stock(stock)),
        )
    }

    private fun stockOf(product: Product) = productJpaRepository.findById(product.id).get().stock

    /**
     * 모든 스레드를 한 신호에 함께 출발시킨다. 순차 실행이 되면 경쟁이 일어나지 않아
     * 테스트가 통과해 버린다.
     */
    private fun runTogether(count: Int, task: (Int) -> Unit): Int {
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(count)

        repeat(count) { index ->
            pool.submit {
                start.await()
                try {
                    task(index)
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        pool.shutdown()

        return count - failures.size
    }

    @DisplayName("같은 주문을 동시에 두 번 확정하면, ")
    @Nested
    inner class SameOrder {
        /**
         * 주문 상태가 멱등 장치다. DRAFT -> CONFIRMED 는 한 번만 일어나야 한다.
         */
        @DisplayName("한 번만 성공하고, 재고와 잔액도 한 번만 줄어든다.")
        @Test
        fun confirmsOnce() {
            val userId = 1L
            val product = savedProduct(price = 1_000, stock = 10)
            val order = orderService.create(userId, listOf(OrderCommand.Line(product.id, quantity = 2)))
            pointService.charge(userId, Money(10_000))

            val succeeded = runTogether(2) { orderService.confirm(userId, order.id) }

            assertAll(
                { assertThat(succeeded).isEqualTo(1) },
                { assertThat(stockOf(product)).isEqualTo(Stock(8)) },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(8_000)) },
            )
        }
    }

    @DisplayName("재고 1개를 두 주문이 동시에 확정하면, ")
    @Nested
    inner class SameProduct {
        @DisplayName("한 명만 성공하고, 재고는 음수가 되지 않는다.")
        @Test
        fun sellsOnlyWhatIsInStock() {
            val product = savedProduct(price = 1_000, stock = 1)
            val orders = (1L..2L).map { userId ->
                pointService.charge(userId, Money(10_000))
                userId to orderService.create(userId, listOf(OrderCommand.Line(product.id, quantity = 1)))
            }

            val succeeded = runTogether(2) { index ->
                val (userId, order) = orders[index]
                orderService.confirm(userId, order.id)
            }

            assertAll(
                { assertThat(succeeded).isEqualTo(1) },
                { assertThat(stockOf(product)).isEqualTo(Stock(0)) },
            )
        }
    }

    @DisplayName("한 사용자가 두 주문을 동시에 확정하면, ")
    @Nested
    inner class SameBalance {
        /**
         * 잔액 10,000 으로 7,000 짜리 둘을 살 수는 없다. 하나는 잔액 부족으로 막혀야 한다.
         */
        @DisplayName("잔액이 감당하는 만큼만 성공한다.")
        @Test
        fun paysOnlyWhatBalanceAllows() {
            val userId = 1L
            pointService.charge(userId, Money(10_000))
            val orders = (1..2).map {
                val product = savedProduct(price = 7_000, stock = 5)
                orderService.create(userId, listOf(OrderCommand.Line(product.id, quantity = 1)))
            }

            val succeeded = runTogether(2) { index -> orderService.confirm(userId, orders[index].id) }

            assertAll(
                { assertThat(succeeded).isEqualTo(1) },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(3_000)) },
            )
        }
    }
}
