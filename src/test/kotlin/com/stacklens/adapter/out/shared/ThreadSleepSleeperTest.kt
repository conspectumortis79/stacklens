package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Test

class ThreadSleepSleeperTest {
    @Test
    fun `sleeps for the given duration`() {
        val sleeper = ThreadSleepSleeper()
        val start = System.nanoTime()
        sleeper.sleep(50)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        assert(elapsed >= 45) { "expected at least 45 ms, got $elapsed" }
    }

    @Test
    fun `sleep zero is a no-op`() {
        val sleeper = ThreadSleepSleeper()
        sleeper.sleep(0)
    }

    @Test
    fun `sleep restores interrupt flag when interrupted`() {
        val sleeper = ThreadSleepSleeper()
        Thread.interrupted()
        Thread.currentThread().interrupt()
        sleeper.sleep(60_000L)
        assert(Thread.interrupted()) { "Interrupt flag should be set after interrupted sleep" }
    }
}
