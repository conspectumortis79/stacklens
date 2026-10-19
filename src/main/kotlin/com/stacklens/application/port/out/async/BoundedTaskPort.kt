package com.stacklens.application.port.out.async

fun interface BoundedTaskPort {
    fun runBounded(
        name: String,
        timeoutMillis: Long,
        task: () -> Unit,
    ): Boolean
}
