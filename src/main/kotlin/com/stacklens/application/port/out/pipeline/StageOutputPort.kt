package com.stacklens.application.port.out.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun

interface StageOutputPort {
    fun write(
        run: AgentPipelineRun,
        stageName: String,
        content: String,
    )
}
