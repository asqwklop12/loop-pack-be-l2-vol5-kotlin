package com.loopers.domain.product

interface ProductStatsLock {
    fun tryAcquire(): Boolean

    fun release()
}
