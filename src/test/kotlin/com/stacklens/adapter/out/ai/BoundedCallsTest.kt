package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BoundedCallsTest {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val interrupter = Executors.newSingleThreadScheduledExecutor()

    @AfterEach
    fun stopExecutor() {
        executor.shutdownNow()
        interrupter.shutdownNow()
    }

    @Test
    fun `a task finishing within the budget runs`() {
        val ran = AtomicBoolean(false)

        BoundedCalls.runWithin(5L, executor, "resolver") { ran.set(true) }

        assertTrue(ran.get())
    }

    @Test
    fun `a task outliving the budget raises a timeout io exception`() {
        val failure =
            assertThrows(IOException::class.java) {
                BoundedCalls.runWithin(1L, executor, "resolver") { Thread.sleep(5_000L) }
            }

        assertEquals("resolver timed out after 1s", failure.message)
    }

    @Test
    fun `an io exception from the task is rethrown unchanged`() {
        val original = IOException("DNS pre-resolution failed for api.example")

        val failure =
            assertThrows(IOException::class.java) {
                BoundedCalls.runWithin(5L, executor, "resolver") { throw original }
            }

        assertSame(original, failure)
    }

    @Test
    fun `a non io failure is wrapped into an io exception`() {
        val failure =
            assertThrows(IOException::class.java) {
                BoundedCalls.runWithin(5L, executor, "resolver") { throw IllegalStateException("boom") }
            }

        assertTrue(failure.message!!.startsWith("resolver failed"), failure.message)
    }

    @Test
    fun `an already interrupted caller reports the interruption and keeps the flag set`() {
        Thread.currentThread().interrupt()

        val failure =
            assertThrows(IOException::class.java) {
                BoundedCalls.runWithin(5L, executor, "resolver") { }
            }

        assertEquals("resolver was interrupted", failure.message)
        assertTrue(Thread.interrupted())
    }

    @Test
    fun `a caller interrupted while the task is still running cancels it and keeps the flag set`() {
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        var message: String? = null
        var flagAfter = false

        val caller =
            Thread {
                try {
                    BoundedCalls.runWithin(5L, executor, "resolver") {
                        running.countDown()
                        runCatching { release.await() }
                    }
                } catch (failure: IOException) {
                    message = failure.message
                }
                flagAfter = Thread.currentThread().isInterrupted
            }
        caller.start()

        assertTrue(running.await(5L, TimeUnit.SECONDS), "the task must be running before the caller is interrupted")
        interrupter.schedule({ if (caller.isAlive) caller.interrupt() }, 100L, TimeUnit.MILLISECONDS)
        caller.join(10_000L)
        release.countDown()

        assertFalse(caller.isAlive, "the interrupt must break the wait")
        assertEquals("resolver was interrupted", message)
        assertTrue(flagAfter, "the interrupt flag must stay set for the caller to observe")
    }
}
