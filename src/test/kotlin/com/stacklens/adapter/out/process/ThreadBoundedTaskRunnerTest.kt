package com.stacklens.adapter.out.process

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThreadBoundedTaskRunnerTest {
    private val runner = ThreadBoundedTaskRunner()

    @Test
    fun `a task that finishes inside the budget reports success`() {
        val ran = CountDownLatch(1)

        val finished = runner.runBounded("stacklens-bounded-success", 2_000L) { ran.countDown() }

        assertTrue(finished)
        assertTrue(ran.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `a task that outlives the budget is abandoned without blocking the caller`() {
        val release = CountDownLatch(1)

        val finished =
            try {
                runner.runBounded("stacklens-bounded-timeout", 50L) { release.await(3, TimeUnit.SECONDS) }
            } finally {
                release.countDown()
            }

        assertFalse(finished)
    }
}
