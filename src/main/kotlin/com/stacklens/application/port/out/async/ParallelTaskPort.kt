package com.stacklens.application.port.out.async

import java.util.concurrent.CompletableFuture

interface ParallelTaskPort {
    fun <T> submit(task: () -> T): CompletableFuture<T>
}
