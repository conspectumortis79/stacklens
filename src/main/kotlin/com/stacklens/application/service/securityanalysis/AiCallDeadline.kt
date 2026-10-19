package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.time.ClockPort

class AiCallDeadline(
    private val clock: ClockPort,
    private val budgetSeconds: Long,
) {
    init {
        require(budgetSeconds > 0) { "budgetSeconds must be > 0, was $budgetSeconds" }
    }

    private val startedAtMillis: Long = clock.epochMillis()

    val requestTimeoutSeconds: Long = budgetSeconds + REQUEST_TIMEOUT_GRACE_SECONDS

    fun expired(): Boolean = clock.epochMillis() - startedAtMillis >= budgetSeconds * MILLIS_PER_SECOND

    companion object {
        const val REQUEST_TIMEOUT_GRACE_SECONDS: Long = 5L
        private const val MILLIS_PER_SECOND: Long = 1_000L
    }
}
