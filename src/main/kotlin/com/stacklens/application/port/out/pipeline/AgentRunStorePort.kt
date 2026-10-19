package com.stacklens.application.port.out.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun

interface AgentRunStorePort {
    fun loadAll(): List<AgentPipelineRun>

    fun save(run: AgentPipelineRun)

    fun delete(runId: Long)
}
