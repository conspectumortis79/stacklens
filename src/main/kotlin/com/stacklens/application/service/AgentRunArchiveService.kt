package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.out.pipeline.AgentRunStorePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@ApplicationService
@ManuallyWired
class AgentRunArchiveService(
    private val store: AgentRunStorePort,
    private val runs: ConcurrentHashMap<Long, AgentPipelineRun>,
    private val maxRetainedRuns: Int,
    private val seedRunId: (Long) -> Unit,
) {
    private val log = LoggerFactory.getLogger(AgentRunArchiveService::class.java)

    fun loadFromDisk() {
        val loaded = store.loadAll()
        loaded.forEach { run -> runs[run.id] = run }
        val maxId = loaded.maxOfOrNull { it.id } ?: 0L
        if (maxId > 0) seedRunId(maxId)
        if (loaded.isNotEmpty()) log.info("Loaded {} pipeline run(s) from disk", loaded.size)
    }

    fun persist(run: AgentPipelineRun) {
        store.save(run)
    }

    fun persistAndEvict(run: AgentPipelineRun) {
        persist(run)
        evictOld()
    }

    fun evictOld() {
        if (runs.size <= maxRetainedRuns) return
        val evictable =
            runs.values
                .filter { it.status == PipelineStatus.DONE || it.status == PipelineStatus.ERROR }
                .sortedBy { it.id }
        val toEvict = evictable.take((runs.size - maxRetainedRuns).coerceAtLeast(0))
        toEvict.forEach { run ->
            runs.remove(run.id)
            store.delete(run.id)
        }
        if (toEvict.isNotEmpty()) log.info("Evicted {} old pipeline run(s) from memory and disk", toEvict.size)
    }

    fun deleteFile(runId: Long) {
        store.delete(runId)
    }
}
