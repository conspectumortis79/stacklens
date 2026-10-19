package com.stacklens.application.port.out.process

fun interface WorkerHandle {
    fun interrupt()
}

interface WorkerStarterPort {
    fun start(
        name: String,
        task: () -> Unit,
    ): WorkerHandle
}
