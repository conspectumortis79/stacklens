package com.stacklens.adapter.out.async

import com.stacklens.application.port.out.async.StartupTaskExecutor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

class BackgroundStartupTaskExecutor : StartupTaskExecutor {
    private val seq = AtomicLong(0)
    private val threadFactory =
        ThreadFactory { runnable ->
            val name = "stacklens-startup-${seq.incrementAndGet()}"
            Thread(runnable, name)
                .apply { isDaemon = true }
        }
    private val executor = Executors.newSingleThreadExecutor(threadFactory)

    override fun submit(task: () -> Unit) {
        executor.execute(task)
    }
}
