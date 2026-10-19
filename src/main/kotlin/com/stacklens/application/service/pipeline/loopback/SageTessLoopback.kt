package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.sageOutput
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class SageTessLoopback : PipelineLoopback {
    override val name: String = "Sage→Tess"
    override val afterStage: String = "sage"
    private val log = LoggerFactory.getLogger(SageTessLoopback::class.java)

    override fun runIfRequested(ctx: PipelineContext): Boolean {
        val run = ctx.run
        val sageOutput = ctx.sageOutput
        if (!sageOutput.contains(HANDOFF_TO_TESS)) return false

        log.info("[Pipeline {}] Sage requested Tess redux — re-running Tess + Quinn", run.id)
        val sageHandoffBlock = sageOutput.substringAfter(HANDOFF_TO_TESS).trim()

        runTessWithSageHandoff(ctx, sageHandoffBlock, log)
        if (run.cancelled) return true
        runSydWithSageHandoff(ctx, sageHandoffBlock, log)
        if (run.cancelled) return true
        runQuinnWithUpdatedTessAndSyd(ctx, log)
        return true
    }
}
