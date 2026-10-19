package com.stacklens.adapter.out.async

import com.stacklens.application.port.out.async.ParallelTaskPort
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
object ForkJoinParallelTaskPort : ParallelTaskPort {
    override fun <T> submit(task: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync { task() }
}
