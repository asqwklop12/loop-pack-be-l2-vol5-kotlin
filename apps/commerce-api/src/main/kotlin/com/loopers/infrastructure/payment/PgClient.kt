package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentTransaction
import com.loopers.domain.shared.Money
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class PgClient(
    private val properties: PgProperties,
) : PaymentGateway {
    private val restClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout.toMillis().toInt())
                setReadTimeout(properties.readTimeout.toMillis().toInt())
            },
        )
        .build()

    @CircuitBreaker(name = PG)
    override fun request(orderId: Long, amount: Money, card: CardInfo): PaymentTransaction =
        restClient.post()
            .uri("/api/v1/payments")
            .body(
                PgDto.PaymentRequest(
                    orderId = orderId,
                    amount = amount.amount,
                    cardType = card.type,
                    cardNo = card.number,
                ),
            )
            .retrieve()
            .body<PgDto.PaymentResponse>()
            ?.toTransaction()
            ?: throw IllegalStateException("PG 응답 본문이 비어 있습니다. [orderId = $orderId]")

    // 조회에는 서킷을 걸지 않는다. 접수가 막힌 동안 우리가 모르는 거래를 찾아오는 경로다.
    override fun findByOrderId(orderId: Long): PaymentTransaction? =
        runCatching {
            restClient.get()
                .uri { it.path("/api/v1/payments").queryParam("orderId", orderId).build() }
                .retrieve()
                .body<PgDto.PaymentResponse>()
                ?.toTransaction()
        }.getOrElse { if (it is HttpClientErrorException.NotFound) null else throw it }

    companion object {
        const val PG = "pg"
    }
}
