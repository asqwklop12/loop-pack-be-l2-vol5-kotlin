package com.loopers.application.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PaymentTransaction
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderPaymentIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val pointService: PointService,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var paymentGateway: PaymentGateway

    private val userId = 1L
    private val card = CardInfo(type = "SAMSUNG", number = "1234-5678-9814-1451")

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun savedProduct(price: Long = 1_000, stock: Int = 5): Product {
        val brand = brandJpaRepository.save(Brand(name = "테스트브랜드"))

        return productJpaRepository.save(
            Product(brandId = brand.id, name = "상품", price = Money(price), stock = Stock(stock)),
        )
    }

    private fun draftOrder(price: Long = 1_000, quantity: Int = 1): Long {
        val product = savedProduct(price = price)

        return orderFacade.create(userId, listOf(OrderCommand.Line(product.id, quantity))).id
    }

    @DisplayName("카드를 넘기면 PG 로 결제하고 포인트는 건드리지 않는다.")
    @Test
    fun paysThroughGateway() {
        pointService.charge(userId, Money(10_000))
        val orderId = draftOrder(price = 1_000)
        val amount = slot<Money>()
        every { paymentGateway.request(any(), capture(amount), any()) } answers {
            PaymentTransaction(
                transactionKey = "pgtx_1",
                orderId = firstArg(),
                amount = amount.captured,
                maskedCardNo = "****-****-****-1451",
                status = PaymentStatus.PENDING,
            )
        }

        orderFacade.confirm(userId, orderId, PaymentMethod.CARD, card)

        assertAll(
            { verify(exactly = 1) { paymentGateway.request(orderId, Money(1_000), card) } },
            { assertThat(amount.captured).isEqualTo(Money(1_000)) },
            { assertThat(pointService.getBalance(userId)).isEqualTo(Money(10_000)) },
        )
    }

    @DisplayName("카드를 넘기지 않으면 포인트로 결제하고 PG 를 부르지 않는다.")
    @Test
    fun paysWithPoint() {
        pointService.charge(userId, Money(10_000))
        val orderId = draftOrder(price = 1_000)

        orderFacade.confirm(userId, orderId)

        assertAll(
            { verify(exactly = 0) { paymentGateway.request(any(), any(), any()) } },
            { assertThat(pointService.getBalance(userId)).isEqualTo(Money(9_000)) },
        )
    }

    @DisplayName("PG 호출이 실패하면 재고와 주문이 되돌아간다.")
    @Test
    fun rollsBackWhenGatewayFails() {
        val product = savedProduct(price = 1_000, stock = 5)
        val orderId = orderFacade.create(userId, listOf(OrderCommand.Line(product.id, 2))).id
        every { paymentGateway.request(any(), any(), any()) } throws IllegalStateException("PG 응답 없음")

        runCatching { orderFacade.confirm(userId, orderId, PaymentMethod.CARD, card) }

        assertAll(
            { assertThat(productJpaRepository.findById(product.id).get().stock.quantity).isEqualTo(5) },
            { assertThat(orderFacade.get(userId, orderId).status).isEqualTo("DRAFT") },
        )
    }
}
