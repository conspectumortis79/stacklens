package com.stacklens.infrastructure.startup

import com.stacklens.adapter.out.async.BackgroundStartupTaskExecutor
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StartupTaskExecutorTest {
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `BackgroundStartupTaskExecutor runs the task on a worker thread, not the caller`() {
        val callerThread = Thread.currentThread()
        val workerThread = arrayOfNulls<Thread>(1)
        val latch = CountDownLatch(1)
        val executor = BackgroundStartupTaskExecutor()
        executor.submit {
            workerThread[0] = Thread.currentThread()
            latch.countDown()
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS), "background task must complete")
        assertNotEquals(callerThread, workerThread[0], "background task must not run on the caller thread")
        assertTrue((workerThread[0]?.name ?: "").startsWith("stacklens-startup-"), "worker thread name must follow naming convention")
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `BackgroundStartupTaskExecutor swallows exceptions and lets the JVM default uncaught handler log them`() {
        val latch = CountDownLatch(1)
        val executor = BackgroundStartupTaskExecutor()
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> latch.countDown() }
            executor.submit { throw RuntimeException("boom") }
            assertTrue(latch.await(2, TimeUnit.SECONDS), "uncaughtExceptionHandler must fire when task throws")
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
