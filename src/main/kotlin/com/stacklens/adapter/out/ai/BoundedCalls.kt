package com.stacklens.adapter.out.ai

import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object BoundedCalls {
    fun runWithin(
        timeoutSeconds: Long,
        executor: ExecutorService,
        label: String,
        task: () -> Unit,
    ) {
        if (Thread.currentThread().isInterrupted) throw interrupted(label, null)
        val future: Future<Unit> = executor.submit<Unit> { task() }
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw IOException("$label timed out after ${timeoutSeconds}s", e)
        } catch (e: ExecutionException) {
            throw if (e.cause is IOException) e.cause!! else IOException("$label failed: ${e.message}", e)
        } catch (e: InterruptedException) {
            future.cancel(true)
            throw interrupted(label, e)
        }
    }

    private fun interrupted(
        label: String,
        cause: InterruptedException?,
    ): IOException {
        Thread.currentThread().interrupt()
        return IOException("$label was interrupted", cause)
    }
}
