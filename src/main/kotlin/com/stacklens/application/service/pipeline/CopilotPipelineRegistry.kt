package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

@ApplicationService
@ManuallyWired
class CopilotPipelineRegistry(
    private val executor: Executor,
) {
    private val counter = AtomicLong(0)

    private val runs = ConcurrentHashMap<Long, AgentPipelineRun>()

    fun nextRunId(): Long = counter.incrementAndGet()

    fun seed(persistedMaxId: Long) {
        counter.updateAndGet { current -> if (current > persistedMaxId) current else persistedMaxId }
    }

    fun get(id: Long): AgentPipelineRun? = runs[id]

    fun put(
        id: Long,
        run: AgentPipelineRun,
    ) {
        runs[id] = run
    }

    fun all(): List<AgentPipelineRun> = runs.values.toList()

    internal fun rawMap(): ConcurrentHashMap<Long, AgentPipelineRun> = runs

    fun remove(id: Long) {
        runs.remove(id)
    }

    fun clear() {
        runs.clear()
    }

    fun execute(task: Runnable) {
        executor.execute(task)
    }
}
