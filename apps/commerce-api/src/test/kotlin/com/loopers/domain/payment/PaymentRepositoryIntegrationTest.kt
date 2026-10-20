package com.loopers.domain.payment

import com.loopers.domain.shared.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PaymentRepositoryIntegrationTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("주문과 금액을 저장한다.")
    @Test
    fun saves() {
        val saved = paymentRepository.save(Payment(orderId = 1L, amount = Money(50_000)))

        assertAll(
            { assertThat(saved.id).isPositive() },
            { assertThat(saved.orderId).isEqualTo(1L) },
            { assertThat(saved.amount).isEqualTo(Money(50_000)) },
            { assertThat(saved.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(saved.createdAt).isNotNull() },
        )
    }

    @DisplayName("주문으로 찾는다.")
    @Test
    fun findsByOrderId() {
        paymentRepository.save(Payment(orderId = 1L, amount = Money(50_000)))
        paymentRepository.save(Payment(orderId = 2L, amount = Money(30_000)))

        assertAll(
            { assertThat(paymentRepository.findAllByOrderId(1L)).hasSize(1) },
            { assertThat(paymentRepository.findAllByOrderId(1L).first().amount).isEqualTo(Money(50_000)) },
            { assertThat(paymentRepository.findAllByOrderId(99L)).isEmpty() },
        )
    }

    @DisplayName("상태를 바꾼다.")
    @Test
    fun changesStatus() {
        val saved = paymentRepository.save(Payment(orderId = 1L, amount = Money(50_000)))

        saved.markSuccess()
        paymentRepository.save(saved)

        assertThat(paymentRepository.findAllByOrderId(1L).first().status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @DisplayName("한 주문에 여러 건을 남길 수 있다.")
    @Test
    fun allowsManyPerOrder() {
        paymentRepository.save(Payment(orderId = 1L, amount = Money(50_000)))
        paymentRepository.save(Payment(orderId = 1L, amount = Money(50_000)))

        assertThat(paymentRepository.findAllByOrderId(1L)).hasSize(2)
    }
}
