package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.sydOutput
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class SydFinnLoopback : PipelineLoopback {
    override val name: String = "Syd↔Finn"
    override val afterStage: String = "syd"
    private val log = LoggerFactory.getLogger(SydFinnLoopback::class.java)

    override fun runIfRequested(ctx: PipelineContext): Boolean {
        val run = ctx.run
        val sydOutput = ctx.sydOutput
        if (!sydOutput.contains(HANDOFF_TO_FINN)) return false

        log.info("[Pipeline {}] Syd requested Finn loopback — re-running Finn with handoff", run.id)
        val handoffBlock = sydOutput.substringAfter(HANDOFF_TO_FINN).trim()
        val sydHandoff = "### Handoff from Syd\n\n$handoffBlock"

        runFinnWithHandoff(ctx, sydHandoff, "[Pipeline {}] Finn syd-fix-pass done ({} chars)", log)
        if (run.cancelled) return true
        runTessWithUpdatedFinn(ctx, log)
        if (run.cancelled) return true
        runSydWithUpdatedFinnAndTess(ctx, log)
        return true
    }
}
