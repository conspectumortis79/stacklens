package com.stacklens.testsupport

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DirectExecutorService :
    AbstractExecutorService(),
    ExecutorService {
    val submittedTasks: MutableList<Runnable> = mutableListOf()

    private var closed = false

    override fun execute(command: Runnable) {
        if (closed) return
        submittedTasks += command
        command.run()
        if (command is java.util.concurrent.Future<*>) {
            try {
                command.get()
            } catch (e: java.util.concurrent.ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    override fun shutdown() {
        closed = true
    }

    override fun shutdownNow(): List<Runnable> {
        closed = true
        return emptyList()
    }

    override fun isShutdown(): Boolean = closed

    override fun isTerminated(): Boolean = closed

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = true
}

class ScheduledExecutorStub(
    private val delegate: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : ScheduledExecutorService by delegate {
    val scheduledTasks: MutableList<Runnable> = mutableListOf()

    var shutdownNowCalls = 0

    override fun scheduleAtFixedRate(
        command: Runnable,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit,
    ): ScheduledFuture<*> {
        scheduledTasks += command
        return CapturedScheduledFuture
    }

    override fun scheduleWithFixedDelay(
        command: Runnable,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit,
    ): ScheduledFuture<*> {
        scheduledTasks += command
        return CapturedScheduledFuture
    }

    override fun shutdownNow(): List<Runnable> {
        shutdownNowCalls++
        return delegate.shutdownNow()
    }

    fun runScheduledTasks() {
        scheduledTasks.toList().forEach { it.run() }
    }

    private object CapturedScheduledFuture : ScheduledFuture<Any?> {
        override fun getDelay(unit: TimeUnit): Long = 0L

        override fun compareTo(other: java.util.concurrent.Delayed): Int = 0

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = true

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean = true

        override fun get(): Any? = null

        override fun get(
            timeout: Long,
            unit: TimeUnit,
        ): Any? = null
    }
}
