package com.stacklens.application.service.pipeline.loopback

import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.service.AgentContextHelpers
import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.application.service.pipeline.coleOutput
import com.stacklens.application.service.pipeline.fayeOutput
import com.stacklens.application.service.pipeline.finnOutput
import com.stacklens.application.service.pipeline.maxOutput
import com.stacklens.application.service.pipeline.noraOutput
import com.stacklens.application.service.pipeline.quinnOutput
import com.stacklens.application.service.pipeline.sydOutput
import com.stacklens.application.service.pipeline.tessOutput
import org.slf4j.Logger

internal fun stripMarker(
    output: String,
    marker: String,
    pipelineId: Long,
    loopbackName: String,
    log: Logger,
): String =
    if (output.contains(marker)) {
        log.warn(
            "[Pipeline {}] {} emitted {} again — second loopback not supported, marker stripped",
            pipelineId,
            loopbackName,
            marker,
        )
        output.substringBefore(marker).trim()
    } else {
        output
    }

internal fun runFinnWithHandoff(
    ctx: PipelineContext,
    handoffBlock: String,
    successLogTemplate: String,
    log: Logger,
    fixPassLabel: String = "Finn fix-pass",
) {
    val run = ctx.run
    run.currentStage = "finn"
    run.finn.status = StageStatus.RUNNING
    val baseFinn = ctx.finnOutput
    val prompt =
        ctx.loadAgentPrompt(
            "finn",
            mapOf(
                "noraOutput" to ctx.noraOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "repoStructure" to ctx.repoStructure,
                "tessHandoff" to handoffBlock,
                "dbSchema" to run.dbSchema,
            ),
        )
    val newFinn =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial ->
            run.finn.output = baseFinn + "\n\n---\n🔄 $fixPassLabel:\n" + partial
        }
    run.finn.output = baseFinn + "\n\n---\n🔄 $fixPassLabel:\n$newFinn"
    run.finn.status = StageStatus.DONE
    ctx.writeStageOutput(run, "finn", run.finn.output)
    ctx.finnOutput = run.finn.output
    log.info(successLogTemplate, run.id, newFinn.length)
}

internal fun runFayeWithUpdatedFinn(
    ctx: PipelineContext,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "faye"
    run.faye.status = StageStatus.RUNNING
    val fayeMockupSection = AgentContextHelpers.buildMockupSection(run.mockupImages)
    val prompt =
        ctx.loadAgentPrompt(
            "faye",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "repoStructure" to ctx.repoStructure,
                "mockupSection" to fayeMockupSection,
            ),
        )
    val rawFaye = ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.faye.output = partial }
    ctx.fayeOutput = stripMarker(rawFaye, HANDOFF_TO_FINN, run.id, "Faye (finn-fix-pass)", log)
    run.faye.output = ctx.fayeOutput
    run.faye.status = StageStatus.DONE
    ctx.writeStageOutput(run, "faye", ctx.fayeOutput)
    log.info("[Pipeline {}] Faye finn-fix-pass done ({} chars)", run.id, ctx.fayeOutput.length)
}

internal fun runTessWithUpdatedFinn(
    ctx: PipelineContext,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "tess"
    run.tess.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "tess",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "quinnHandoff" to "",
                "sageHandoff" to "",
                "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                "dbSchema" to run.dbSchema,
            ),
        )
    val newTess =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.tess.output = partial }
    ctx.tessOutput = stripMarker(newTess, HANDOFF_TO_FINN, run.id, "Tess (finn-fix-pass)", log)
    run.tess.output = ctx.tessOutput
    run.tess.status = StageStatus.DONE
    ctx.writeStageOutput(run, "tess", ctx.tessOutput)
    log.info("[Pipeline {}] Tess post-finn-fix-pass done ({} chars)", run.id, ctx.tessOutput.length)
}

internal fun runSydWithUpdatedFinnAndTess(
    ctx: PipelineContext,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "syd"
    run.syd.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "syd",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "tessOutput" to ctx.tessOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "quinnHandoff" to "",
                "sageHandoff" to "",
                "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                "dbSchema" to run.dbSchema,
            ),
        )
    val newSyd =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.syd.output = partial }
    ctx.sydOutput = stripMarker(newSyd, HANDOFF_TO_FINN, run.id, "Syd (finn-fix-pass)", log)
    run.syd.output = ctx.sydOutput
    run.syd.status = StageStatus.DONE
    ctx.writeStageOutput(run, "syd", ctx.sydOutput)
    log.info("[Pipeline {}] Syd post-finn-fix-pass done ({} chars)", run.id, ctx.sydOutput.length)
}

internal fun runTessWithQuinnHandoff(
    ctx: PipelineContext,
    quinnHandoffBlock: String,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "tess"
    run.tess.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "tess",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "quinnHandoff" to quinnHandoffBlock,
                "sageHandoff" to "",
                "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                "dbSchema" to run.dbSchema,
            ),
        )
    val newTess =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.tess.output = partial }
    ctx.tessOutput = newTess
    run.tess.output = ctx.tessOutput
    run.tess.status = StageStatus.DONE
    ctx.writeStageOutput(run, "tess", ctx.tessOutput)
    log.info("[Pipeline {}] Tess quinn-redux done ({} chars)", run.id, ctx.tessOutput.length)
}

internal fun runSydWithQuinnHandoff(
    ctx: PipelineContext,
    quinnSydHandoffBlock: String,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "syd"
    run.syd.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "syd",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "tessOutput" to ctx.tessOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "quinnHandoff" to quinnSydHandoffBlock,
                "sageHandoff" to "",
                "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                "dbSchema" to run.dbSchema,
            ),
        )
    val newSyd =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.syd.output = partial }
    ctx.sydOutput = newSyd
    run.syd.output = ctx.sydOutput
    run.syd.status = StageStatus.DONE
    ctx.writeStageOutput(run, "syd", ctx.sydOutput)
    log.info("[Pipeline {}] Syd quinn-redux done ({} chars)", run.id, ctx.sydOutput.length)
}

internal fun runQuinnWithUpdatedTessAndSyd(
    ctx: PipelineContext,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "quinn"
    run.quinn.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "quinn",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "tessOutput" to (ctx.tessOutput + "\n\n---\n## Integration and System Tests (Syd)\n\n" + ctx.sydOutput),
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "coleOutput" to ctx.coleOutput,
            ),
        )
    val newQuinn =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.quinn.output = partial }
    ctx.quinnOutput = newQuinn
    run.quinn.output = ctx.quinnOutput
    run.quinn.status = StageStatus.DONE
    ctx.writeStageOutput(run, "quinn", ctx.quinnOutput)
    log.info("[Pipeline {}] Quinn post-loopback-redux done ({} chars)", run.id, ctx.quinnOutput.length)
}

internal fun runTessWithSageHandoff(
    ctx: PipelineContext,
    sageHandoffBlock: String,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "tess"
    run.tess.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "tess",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "quinnHandoff" to "",
                "sageHandoff" to sageHandoffBlock,
                "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                "dbSchema" to run.dbSchema,
            ),
        )
    val newTess =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.tess.output = partial }
    ctx.tessOutput = newTess
    run.tess.output = ctx.tessOutput
    run.tess.status = StageStatus.DONE
    ctx.writeStageOutput(run, "tess", ctx.tessOutput)
    log.info("[Pipeline {}] Tess sage-security-redux done ({} chars)", run.id, ctx.tessOutput.length)
}

internal fun runSydWithSageHandoff(
    ctx: PipelineContext,
    sageHandoffBlock: String,
    log: Logger,
) {
    val run = ctx.run
    run.currentStage = "syd"
    run.syd.status = StageStatus.RUNNING
    val prompt =
        ctx.loadAgentPrompt(
            "syd",
            mapOf(
                "finnOutput" to ctx.finnOutput,
                "tessOutput" to ctx.tessOutput,
                "coleOutput" to ctx.coleOutput,
                "maxOutput" to ctx.maxOutput,
                "noraOutput" to ctx.noraOutput,
                "quinnHandoff" to "",
                "sageHandoff" to sageHandoffBlock,
                "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                "dbSchema" to run.dbSchema,
            ),
        )
    val newSyd =
        ctx.stream(ctx.model, prompt, ctx.repoRoot) { partial -> run.syd.output = partial }
    ctx.sydOutput = newSyd
    run.syd.output = ctx.sydOutput
    run.syd.status = StageStatus.DONE
    ctx.writeStageOutput(run, "syd", ctx.sydOutput)
    log.info("[Pipeline {}] Syd sage-security-redux done ({} chars)", run.id, ctx.sydOutput.length)
}
