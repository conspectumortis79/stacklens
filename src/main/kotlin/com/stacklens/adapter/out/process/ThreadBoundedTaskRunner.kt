package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.async.BoundedTaskPort
import org.springframework.stereotype.Component

@Component
class ThreadBoundedTaskRunner : BoundedTaskPort {
    override fun runBounded(
        name: String,
        timeoutMillis: Long,
        task: () -> Unit,
    ): Boolean {
        val worker =
            Thread(task, name).apply {
                isDaemon = true
                start()
            }
        worker.join(timeoutMillis)
        return !worker.isAlive
    }
}
