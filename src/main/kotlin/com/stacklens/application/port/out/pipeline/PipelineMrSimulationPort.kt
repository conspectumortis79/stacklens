package com.stacklens.application.port.out.pipeline

import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult

interface PipelineMrSimulationPort {
    fun simulatedResult(
        analysis: PipelineAnalysis,
        gitlabAvailable: Boolean,
    ): PipelineMrResult
}
