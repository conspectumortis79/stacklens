package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.async.DockerTestAsyncJobDispatcher
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DockerTestAsyncJobDispatcherTest {
    @Test
    fun `the dispatcher hands the stream task to the executor`() {
        val executor = Executors.newSingleThreadExecutor()
        val ran = java.util.concurrent.CountDownLatch(1)

        DockerTestAsyncJobDispatcher(executor).submitStream { ran.countDown() }

        assertTrue(ran.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()
    }
}
