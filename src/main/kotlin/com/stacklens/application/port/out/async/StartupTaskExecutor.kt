package com.stacklens.application.port.out.async

fun interface StartupTaskExecutor {
    fun submit(task: () -> Unit)
}
