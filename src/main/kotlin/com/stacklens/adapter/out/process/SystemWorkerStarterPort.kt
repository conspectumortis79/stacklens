package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.process.WorkerHandle
import com.stacklens.application.port.out.process.WorkerStarterPort
import org.springframework.stereotype.Component

@Component
object SystemWorkerStarterPort : WorkerStarterPort {
    override fun start(
        name: String,
        task: () -> Unit,
    ): WorkerHandle {
        val thread = Thread(task, name).apply { isDaemon = true }
        thread.start()
        return WorkerHandle { thread.interrupt() }
    }
}
