package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.fayeOutput
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class FayeFinnLoopback : PipelineLoopback {
    override val name: String = "Faye↔Finn"
    override val afterStage: String = "faye"
    private val log = LoggerFactory.getLogger(FayeFinnLoopback::class.java)

    override fun runIfRequested(ctx: PipelineContext): Boolean {
        val run = ctx.run
        val fayeOutput = ctx.fayeOutput
        if (!fayeOutput.contains(HANDOFF_TO_FINN)) return false

        log.info("[Pipeline {}] Faye requested Finn loopback — re-running Finn with handoff", run.id)
        val handoffBlock = fayeOutput.substringAfter(HANDOFF_TO_FINN).trim()
        val fayeHandoff = "### Handoff from Faye\n\n$handoffBlock"

        runFinnWithHandoff(ctx, fayeHandoff, "[Pipeline {}] Finn faye-fix-pass done ({} chars)", log, "Finn fix-pass (Faye)")
        if (run.cancelled) return true
        runFayeWithUpdatedFinn(ctx, log)
        return true
    }
}
