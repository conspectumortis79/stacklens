package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.ThreadSleepSleeper
import com.stacklens.application.port.out.process.SleeperPort
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class HttpSendRetry(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS,
    private val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
    private val sleeper: SleeperPort = ThreadSleepSleeper(),
    private val log: Logger = LoggerFactory.getLogger(HttpSendRetry::class.java),
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        require(initialBackoffMs >= 0) { "initialBackoffMs must be >= 0, was $initialBackoffMs" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0, was $backoffMultiplier" }
    }

    fun <T> sendWithRetry(
        opName: String,
        block: () -> T,
    ): T {
        var attempt = 0
        var backoff = initialBackoffMs
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: IOException) {
                if (attempt >= maxAttempts) {
                    log.error("[{}] giving up after {} attempt(s): {}", opName, attempt, e.toString())
                    throw IllegalStateException(
                        "HTTP request '$opName' failed after $attempt attempt(s); last error: $e",
                        e,
                    )
                }
                log.warn(
                    "[{}] attempt {}/{} failed ({}); retrying in {} ms",
                    opName,
                    attempt,
                    maxAttempts,
                    e.toString(),
                    backoff,
                )
                if (backoff > 0 && !sleeper.sleep(backoff)) {
                    Thread.currentThread().interrupt()
                    throw InterruptedException("HTTP retry '$opName' interrupted after attempt $attempt")
                }
                backoff = (backoff * backoffMultiplier).toLong().coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS: Int = 3

        const val DEFAULT_INITIAL_BACKOFF_MS: Long = 1_000L

        const val DEFAULT_BACKOFF_MULTIPLIER: Double = 2.0

        const val MAX_BACKOFF_MS: Long = 30_000L
    }
}
