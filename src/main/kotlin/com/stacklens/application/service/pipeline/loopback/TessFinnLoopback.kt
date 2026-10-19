package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.tessOutput
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class TessFinnLoopback : PipelineLoopback {
    override val name: String = "Tess↔Finn"
    override val afterStage: String = "tess"
    private val log = LoggerFactory.getLogger(TessFinnLoopback::class.java)

    override fun runIfRequested(ctx: PipelineContext): Boolean {
        val run = ctx.run
        val tessOutput = ctx.tessOutput
        if (!tessOutput.contains(HANDOFF_TO_FINN)) return false

        log.info("[Pipeline {}] Tess requested Finn loopback — re-running Finn with handoff", run.id)
        val handoffBlock = tessOutput.substringAfter(HANDOFF_TO_FINN).trim()
        val tessHandoff = "### Handoff from Tess\n\n$handoffBlock"

        runFinnWithHandoff(ctx, tessHandoff, "[Pipeline {}] Finn fix-pass done ({} chars)", log)
        if (run.cancelled) return true
        runTessWithUpdatedFinn(ctx, log)
        return true
    }
}
