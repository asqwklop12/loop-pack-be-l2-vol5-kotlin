package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
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
 * 확정과 취소는 재고와 포인트를 함께 움직인다. 같은 행을 동시에 건드리면 읽기와 쓰기 사이에
 * 다른 트랜잭션이 끼어들어 **장부가 어긋난다.**
 *
 * 세 자리를 각각 드러낸다. 막는 자리가 달라서 한 번에 묶이지 않는다.
 * - 같은 주문을 두 번 확정 — 주문 행
 * - 한 상품을 여러 주문이 확정 — 상품 행
 * - 한 사용자가 여러 주문을 확정 — 포인트 행
 */
@SpringBootTest
class OrderConcurrencyIntegrationTest @Autowired constructor(
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
    private fun runTogether(count: Int, task: (Int) -> Unit): Outcome {
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val rejected = Collections.synchronizedList(mutableListOf<CoreException>())
        val failed = Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(count)

        repeat(count) { index ->
            pool.submit {
                try {
                    start.await()
                    task(index)
                } catch (e: CoreException) {
                    rejected.add(e)
                } catch (e: Throwable) {
                    failed.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()

        val finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pool.shutdown()
        val terminated = pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // 멈춘 작업은 아무 갈래에도 안 들어가 성공처럼 보인다. 집계 전에 거른다
        assertAll(
            { assertThat(finished).`as`("모든 작업이 제한 시간 안에 끝난다").isTrue() },
            { assertThat(terminated).`as`("스레드가 모두 정리된다").isTrue() },
        )

        return Outcome(
            succeeded = count - rejected.size - failed.size,
            rejected = rejected.size,
            failed = failed.size,
        )
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

            val outcome = runTogether(2) { orderService.confirm(userId, order.id) }

            assertAll(
                { assertThat(outcome.succeeded).isEqualTo(1) },
                { assertThat(outcome.blocked).`as`("진 쪽은 한 건").isEqualTo(1) },
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

            val outcome = runTogether(2) { index ->
                val (userId, order) = orders[index]
                orderService.confirm(userId, order.id)
            }

            assertAll(
                { assertThat(outcome.succeeded).isEqualTo(1) },
                { assertThat(outcome.blocked).`as`("진 쪽은 한 건").isEqualTo(1) },
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

            val outcome = runTogether(2) { index -> orderService.confirm(userId, orders[index].id) }

            assertAll(
                { assertThat(outcome.succeeded).isEqualTo(1) },
                // 비관락이라 기다린 쪽이 갱신된 잔액을 읽는다. 충돌이 아니라 잔액 부족으로 막힌다
                { assertThat(outcome.rejected).`as`("업무 거절 — 잔액 부족").isEqualTo(1) },
                { assertThat(outcome.failed).`as`("기술 오류").isZero() },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(3_000)) },
            )
        }
    }

    @DisplayName("확정된 주문을 동시에 두 번 취소하면, ")
    @Nested
    inner class SameOrderCancel {
        /**
         * 취소 따닥은 확정 따닥보다 나쁘다. 확정이 두 번이면 돈이 두 번 나가지만,
         * 취소가 두 번이면 **없던 돈이 생긴다.**
         */
        @DisplayName("한 번만 성공하고, 환불과 재고 복원도 한 번만 일어난다.")
        @Test
        fun cancelsOnce() {
            val userId = 1L
            val product = savedProduct(price = 7_000, stock = 5)
            val order = orderService.create(userId, listOf(OrderCommand.Line(product.id, quantity = 1)))
            pointService.charge(userId, Money(10_000))
            orderService.confirm(userId, order.id)

            val outcome = runTogether(2) { orderService.cancel(userId, order.id) }

            assertAll(
                { assertThat(outcome.succeeded).isEqualTo(1) },
                { assertThat(outcome.blocked).`as`("진 쪽은 한 건").isEqualTo(1) },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(10_000)) },
                { assertThat(stockOf(product)).isEqualTo(Stock(5)) },
            )
        }
    }

    @DisplayName("한 주문을 확정과 취소가 동시에 건드리면, ")
    @Nested
    inner class ConfirmAndCancel {
        /**
         * 확정하면서 동시에 취소되는 주문은 없어야 한다. 둘 다 DRAFT 를 읽으므로
         * 상태 검사만으로는 갈리지 않는다.
         */
        @DisplayName("둘 중 하나만 성공한다.")
        @Test
        fun onlyOneWins() {
            val userId = 1L
            val product = savedProduct(price = 7_000, stock = 5)
            val order = orderService.create(userId, listOf(OrderCommand.Line(product.id, quantity = 1)))
            pointService.charge(userId, Money(10_000))

            val outcome = runTogether(2) { index ->
                if (index == 0) {
                    orderService.confirm(userId, order.id)
                } else {
                    orderService.cancel(userId, order.id)
                }
            }

            assertAll(
                { assertThat(outcome.succeeded).isEqualTo(1) },
                { assertThat(outcome.blocked).`as`("진 쪽은 한 건").isEqualTo(1) },
            )
        }
    }

    @DisplayName("충전과 주문 확정이 동시에 일어나면, ")
    @Nested
    inner class ChargeAndPay {
        /**
         * 앞의 경우들과 반대다. 저 둘은 **막아야 맞는** 것이고 이건 **둘 다 되어야 맞는** 것이다.
         * 10,000 + 2,000 - 7,000 = 5,000 으로 잔액이 성립한다.
         *
         * 락이 과하게 막는지를 보는 자리다. 같은 행을 건드렸다는 이유만으로 떨어뜨리면
         * 정상 동작을 막게 된다.
         */
        @DisplayName("둘 다 성공하고, 잔액은 충전과 결제가 모두 반영된 값이 된다.")
        @Test
        fun appliesBoth() {
            val userId = 1L
            val product = savedProduct(price = 7_000, stock = 10)
            val order = orderService.create(userId, listOf(OrderCommand.Line(product.id, quantity = 1)))
            pointService.charge(userId, Money(10_000))

            val outcome = runTogether(2) { index ->
                if (index == 0) {
                    pointService.charge(userId, Money(2_000))
                } else {
                    orderService.confirm(userId, order.id)
                }
            }

            assertAll(
                { assertThat(outcome.succeeded).isEqualTo(2) },
                { assertThat(outcome.failed).`as`("기술 오류 0").isZero() },
                { assertThat(outcome.rejected).`as`("업무 거절 0").isZero() },
                { assertThat(pointService.getBalance(userId)).isEqualTo(Money(5_000)) },
            )
        }
    }

    /**
     * 실패를 한 덩어리로 세면 진 쪽이 무슨 답을 받았는지 모른다. 성격이 다른 둘이 섞인다.
     *
     * - [rejected] 업무 거절. 재고 부족 · 잔액 부족 · 이미 확정됨. **규칙대로 막힌 정상 동작**
     * - [failed] 기술 오류. 낙관락 충돌 · 타임아웃 · SQL 오류. **경쟁 때문에 못 한 것**
     *
     * 낙관락을 건 자리는 진 쪽이 기술 오류를 받고, 비관락을 건 자리는 기다렸다가 업무 거절을
     * 받는다. 세 갈래로 세야 그 차이가 드러난다.
     */
    private data class Outcome(
        val succeeded: Int,
        val rejected: Int,
        val failed: Int,
    ) {
        /** 어느 갈래로 막혔는지가 경쟁 시점에 따라 갈리는 경우에 쓴다. */
        val blocked: Int get() = rejected + failed
    }

    companion object {
        private const val TIMEOUT_SECONDS = 10L
    }
}
