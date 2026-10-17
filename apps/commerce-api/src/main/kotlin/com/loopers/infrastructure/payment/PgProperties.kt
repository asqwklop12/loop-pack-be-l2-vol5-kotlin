package com.loopers.infrastructure.payment

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pg")
data class PgProperties(
    val baseUrl: String,
    val connectTimeout: Duration,
    val readTimeout: Duration,
)
