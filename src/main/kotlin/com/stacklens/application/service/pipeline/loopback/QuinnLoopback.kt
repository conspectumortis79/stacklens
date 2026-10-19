package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.quinnOutput
import com.stacklens.application.service.pipeline.tessOutput
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class QuinnLoopback : PipelineLoopback {
    override val name: String = "Quinn→{Tess,Syd}"
    override val afterStage: String = "quinn"
    private val log = LoggerFactory.getLogger(QuinnLoopback::class.java)

    override fun runIfRequested(ctx: PipelineContext): Boolean {
        val run = ctx.run
        val quinnOutput = ctx.quinnOutput
        val hasTessHandoff = quinnOutput.contains(HANDOFF_TO_TESS)
        val hasSydHandoff = quinnOutput.contains(HANDOFF_TO_SYD)
        if (!hasTessHandoff && !hasSydHandoff) return false

        log.info(
            "[Pipeline {}] Quinn requested {}loopback — re-running downstream stages",
            run.id,
            if (hasTessHandoff && hasSydHandoff) {
                "Tess+Syd "
            } else if (hasTessHandoff) {
                "Tess "
            } else {
                "Syd "
            },
        )

        val quinnSydHandoffBlock =
            if (hasSydHandoff) quinnOutput.substringAfter(HANDOFF_TO_SYD).trim() else ""

        if (hasTessHandoff) {
            val quinnTessHandoffBlock =
                quinnOutput
                    .substringAfter(HANDOFF_TO_TESS)
                    .substringBefore(HANDOFF_TO_SYD)
                    .trim()
            runTessWithQuinnHandoff(ctx, quinnTessHandoffBlock, log)
            if (run.cancelled) return true
            if (ctx.tessOutput.contains(HANDOFF_TO_FINN)) {
                val tessHandoffBlock = ctx.tessOutput.substringAfter(HANDOFF_TO_FINN).trim()
                runFinnWithHandoff(
                    ctx,
                    "### Handoff from Tess (quinn-redux)\n\n$tessHandoffBlock",
                    "[Pipeline {}] Finn tess-quinn-fix-pass done ({} chars)",
                    log,
                )
                if (run.cancelled) return true
                ctx.tessOutput = ctx.tessOutput.substringBefore(HANDOFF_TO_FINN).trim()
                runTessWithUpdatedFinn(ctx, log)
                if (run.cancelled) return true
            }
        }
        if (hasSydHandoff) {
            runSydWithQuinnHandoff(ctx, quinnSydHandoffBlock, log)
            if (run.cancelled) return true
        }
        runQuinnWithUpdatedTessAndSyd(ctx, log)
        return true
    }
}
