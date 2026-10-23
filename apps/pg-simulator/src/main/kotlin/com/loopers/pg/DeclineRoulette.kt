package com.loopers.pg

import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DeclineRoulette(
    private val properties: PgSimulatorProperties,
) {
    fun spin(random: Random = Random): DeclineReason? {
        var roll = random.nextDouble()

        for (reason in DeclineReason.entries) {
            val rate = properties.decline[reason] ?: 0.0
            if (roll < rate) return reason
            roll -= rate
        }

        return null
    }
}
