package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentTransaction
import com.loopers.domain.shared.Money
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

    override fun find(transactionKey: String): PaymentTransaction? =
        runCatching {
            restClient.get()
                .uri("/api/v1/payments/{transactionKey}", transactionKey)
                .retrieve()
                .body<PgDto.PaymentResponse>()
                ?.toTransaction()
        }.getOrElse { if (it is HttpClientErrorException.NotFound) null else throw it }

    override fun findByOrderId(orderId: Long): PaymentTransaction? =
        runCatching {
            restClient.get()
                .uri { it.path("/api/v1/payments").queryParam("orderId", orderId).build() }
                .retrieve()
                .body<PgDto.PaymentResponse>()
                ?.toTransaction()
        }.getOrElse { if (it is HttpClientErrorException.NotFound) null else throw it }
}
