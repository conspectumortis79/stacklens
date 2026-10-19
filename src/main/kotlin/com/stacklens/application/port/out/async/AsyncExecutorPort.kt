package com.stacklens.application.port.out.async

interface AsyncExecutorPort {
    fun submit(
        executorName: String?,
        task: () -> Unit,
    )
}
