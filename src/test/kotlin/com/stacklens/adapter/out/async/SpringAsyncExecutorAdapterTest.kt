package com.stacklens.adapter.out.async

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class SpringAsyncExecutorAdapterTest {
    private fun await(latch: CountDownLatch) {
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a missing executor name runs the task on the default executor`() {
        val latch = CountDownLatch(1)
        SpringAsyncExecutorAdapter(emptyMap()).submit(null) { latch.countDown() }

        await(latch)
    }

    @Test
    fun `an unknown executor name runs the task on the default executor`() {
        val latch = CountDownLatch(1)
        SpringAsyncExecutorAdapter(emptyMap()).submit("unknown") { latch.countDown() }

        await(latch)
    }

    @Test
    fun `a mapped executor name runs the task on that executor`() {
        val latch = CountDownLatch(1)
        val executor = Executor { latch.countDown() }
        SpringAsyncExecutorAdapter(mapOf("analysis" to executor)).submit("analysis") { }

        await(latch)
    }
}
