package com.loopers.utils

import java.time.Duration

fun <T> eventually(timeout: Duration = Duration.ofSeconds(10), assertion: () -> T): T {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (true) {
        try {
            return assertion()
        } catch (e: AssertionError) {
            if (System.nanoTime() > deadline) throw e
            Thread.sleep(50)
        }
    }
}
