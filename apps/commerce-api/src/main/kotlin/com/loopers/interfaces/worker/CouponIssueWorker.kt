package com.loopers.interfaces.worker

import com.loopers.application.coupon.CouponIssueProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class CouponIssueWorker(
    private val couponIssueProcessor: CouponIssueProcessor,
    @Value("\${coupon.issue.workers:3}") private val workers: Int,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private var executor: ExecutorService? = null

    override fun start() {
        if (workers <= 0 || !running.compareAndSet(false, true)) return
        executor = Executors.newFixedThreadPool(workers) { Thread(it, "coupon-issue-worker").apply { isDaemon = true } }
            .also { pool -> repeat(workers) { pool.submit(::work) } }
    }

    override fun stop() {
        running.set(false)
        executor?.run {
            shutdown()
            awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    override fun isRunning(): Boolean = running.get()

    private fun work() {
        while (running.get()) {
            try {
                couponIssueProcessor.processNext(TAKE_TIMEOUT)
            } catch (e: Exception) {
                log.warn("쿠폰 발급 요청을 처리하지 못했다. cause={}", e.message)
                if (!pause()) return
            }
        }
    }

    private fun pause(): Boolean =
        try {
            Thread.sleep(RETRY_DELAY.toMillis())
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    companion object {
        private val TAKE_TIMEOUT = Duration.ofSeconds(1)
        private val RETRY_DELAY = Duration.ofSeconds(1)
        private val SHUTDOWN_WAIT = Duration.ofSeconds(5)
    }
}
