package com.stacklens.adapter.out.pipeline

import com.stacklens.application.port.out.pipeline.PipelineMrSimulationPort
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PipelineMrSimulationAdapter : PipelineMrSimulationPort {
    private val log = LoggerFactory.getLogger(PipelineMrSimulationAdapter::class.java)

    override fun simulatedResult(
        analysis: PipelineAnalysis,
        gitlabAvailable: Boolean,
    ): PipelineMrResult {
        val url =
            if (gitlabAvailable) {
                "/simulated-pipeline-mr/${analysis.id}"
            } else {
                "/simulated-pipeline-pr/${analysis.id}"
            }
        log.info(
            "[PipelineMrService] Simulated MR/PR for analysis #{} → {}",
            analysis.id,
            url,
        )
        return PipelineMrResult(
            provider = "SIMULATED",
            url = url,
            kind = if (gitlabAvailable) "merge_request" else "pull_request",
            branch = null,
        )
    }
}
