package com.loopers.pg

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pg")
data class PgSimulatorProperties(
    val callbackUrl: String,
    val callbackDelay: Duration = Duration.ofSeconds(3),
    val slowCardDelay: Duration = Duration.ofSeconds(10),
    val declineRate: Double = 0.0,
    val callbackDropRate: Double = 0.0,
)
