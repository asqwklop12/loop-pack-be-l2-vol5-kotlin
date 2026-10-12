package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * **음성 대조군이다. 제품 구현이 아니다.**
 *
 * 갱신 유실이 무엇인지를 돌아가는 코드로 남긴다. 락 없이 같은 행을 동시에 바꾸면
 * 어떻게 어긋나는지를 보여주고, 그 어긋남 자체를 단정한다. 그래서 이 테스트는 **통과한다.**
 *
 * 실제 서비스 검증(`OrderConcurrencyIntegrationTest`)과 섞지 않으려고 파일을 나눴다.
 * 여기에만 **읽기 장벽**이 있다. 둘 다 읽을 때까지 쓰기를 막아 갱신 유실을 확실히 재현한다.
 * 실제 검증에는 이 장벽을 넣지 않는다. 넣으면 진짜 동작이 아니라 꾸며낸 상황을 보게 된다.
 *
 * 엔티티를 쓰지 않고 JDBC 로 직접 쓴다. `Product` 에는 `@Version` 이 있어서 Hibernate 가
 * `where version = ?` 를 붙이는데, 그러면 갱신 유실이 재현되지 않는다. 여기서는
 * **조건도 버전도 없이** 읽어 둔 값에서 1을 뺀 상수를 저장한다.
 */
@SpringBootTest
class StockLostUpdateControlTest @Autowired constructor(
    private val dataSource: DataSource,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("락 없이 재고를 동시에 줄이면, 한 번의 차감이 사라진다.")
    @Test
    fun losesOneDecrease_whenWrittenWithoutLock() {
        // arrange — 커밋된 재고 5
        val brand = brandJpaRepository.save(Brand(name = "테스트브랜드"))
        val product = productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(1_000), stock = Stock(INITIAL)),
        )

        val read = CountDownLatch(WORKERS)
        val start = CountDownLatch(1)
        val done = CountDownLatch(WORKERS)
        val readValues = Collections.synchronizedList(mutableListOf<Int>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(WORKERS)

        repeat(WORKERS) {
            pool.submit {
                try {
                    start.await()
                    dataSource.connection.use { connection ->
                        connection.autoCommit = false

                        // 잠금 없는 읽기
                        val current = connection.prepareStatement(
                            "select stock from product where id = ?",
                        ).use { statement ->
                            statement.setLong(1, product.id)
                            statement.executeQuery().use { rows ->
                                rows.next()
                                rows.getInt(1)
                            }
                        }
                        readValues.add(current)

                        // 읽기 장벽 — 둘 다 읽은 뒤에야 쓴다
                        read.countDown()
                        check(read.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "읽기 장벽 시간 초과" }

                        // 조건도 버전도 없이 상수를 저장한다
                        connection.prepareStatement(
                            "update product set stock = ? where id = ?",
                        ).use { statement ->
                            statement.setInt(1, current - 1)
                            statement.setLong(2, product.id)
                            statement.executeUpdate()
                        }
                        connection.commit()
                    }
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()

        // act
        val finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pool.shutdown()
        val terminated = pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // assert — 재현이 성립했는지부터 본다. 시간 초과나 오류는 갱신 유실이 아니다
        assertAll(
            { assertThat(finished).`as`("모든 작업이 제한 시간 안에 끝난다").isTrue() },
            { assertThat(terminated).`as`("스레드가 모두 정리된다").isTrue() },
            { assertThat(failures).`as`("기술 오류 없이 두 트랜잭션이 커밋된다").isEmpty() },
            { assertThat(readValues).`as`("둘 다 같은 재고를 읽는다").containsExactly(INITIAL, INITIAL) },
        )

        // assert — 불변식이 깨진 것을 단정한다
        val remaining = productJpaRepository.findById(product.id).get().stock.quantity
        assertAll(
            { assertThat(remaining).`as`("두 번 줄였는데 한 번만 줄어 있다").isEqualTo(INITIAL - 1) },
            { assertThat(WORKERS + remaining).`as`("팔린 수량 + 남은 재고 != 처음 재고").isNotEqualTo(INITIAL) },
        )
    }

    companion object {
        private const val INITIAL = 5
        private const val WORKERS = 2
        private const val TIMEOUT_SECONDS = 10L
    }
}
