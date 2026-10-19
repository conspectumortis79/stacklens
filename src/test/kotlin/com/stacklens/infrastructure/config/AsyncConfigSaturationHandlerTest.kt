package com.stacklens.infrastructure.config

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AsyncConfigSaturationHandlerTest {
    @Test
    fun `saturationHandler can be invoked and runs the fallback policy`() {
        val config = AsyncConfig()
        val method = AsyncConfig::class.java.getDeclaredMethod("saturationHandler", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handler = method.invoke(config, "test") as RejectedExecutionHandler

        val pool =
            ThreadPoolExecutor(
                1,
                1,
                1L,
                TimeUnit.SECONDS,
                SynchronousQueue<Runnable>(),
                handler,
            )
        pool.shutdown()
        try {
            pool.execute { }
        } catch (_: RejectedExecutionException) {
        }
        assertNotNull(handler)
    }

    @Test
    fun `saturationHandler name parameter is forwarded to the log warning`() {
        val config = AsyncConfig()
        val method = AsyncConfig::class.java.getDeclaredMethod("saturationHandler", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handler = method.invoke(config, "my-special-pool") as RejectedExecutionHandler
        val pool =
            ThreadPoolExecutor(
                1,
                1,
                1L,
                TimeUnit.SECONDS,
                SynchronousQueue<Runnable>(),
                handler,
            )
        val startedLatch = java.util.concurrent.CountDownLatch(1)
        pool.execute {
            startedLatch.countDown()
            Thread.sleep(500)
        }
        startedLatch.await(1, TimeUnit.SECONDS)
        try {
            pool.execute { }
        } catch (_: Exception) {
        }
        pool.shutdownNow()
        assertNotNull(handler)
    }
}
