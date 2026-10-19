package com.stacklens.testsupport

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object TestExecutors {
    private val counter = AtomicInteger(0)

    fun daemon(): Executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "stacklens-test-${counter.incrementAndGet()}").apply { isDaemon = true }
        }

    fun direct(): Executor = Executor { it.run() }
}
