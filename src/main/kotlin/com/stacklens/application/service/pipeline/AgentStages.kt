package com.stacklens.application.service.pipeline

import com.stacklens.application.service.AgentContextHelpers
import com.stacklens.domain.text.AgentTextUtils

internal var PipelineContext.coleOutput: String
    get() = outputs.of("cole")
    set(value) {
        outputs.record("cole", value)
    }

internal var PipelineContext.maxOutput: String
    get() = outputs.of("max")
    set(value) {
        outputs.record("max", value)
    }

internal var PipelineContext.finnOutput: String
    get() = outputs.of("finn")
    set(value) {
        outputs.record("finn", value)
    }

internal var PipelineContext.fayeOutput: String
    get() = outputs.of("faye")
    set(value) {
        outputs.record("faye", value)
    }

internal var PipelineContext.veraOutput: String
    get() = outputs.of("vera")
    set(value) {
        outputs.record("vera", value)
    }

internal var PipelineContext.tessOutput: String
    get() = outputs.of("tess")
    set(value) {
        outputs.record("tess", value)
    }

internal var PipelineContext.sydOutput: String
    get() = outputs.of("syd")
    set(value) {
        outputs.record("syd", value)
    }

internal var PipelineContext.quinnOutput: String
    get() = outputs.of("quinn")
    set(value) {
        outputs.record("quinn", value)
    }

internal var PipelineContext.sageOutput: String
    get() = outputs.of("sage")
    set(value) {
        outputs.record("sage", value)
    }

class ColeStage : PipelineStage {
    override val name: String = "cole"
    override val rexFeedbackRank: Int? = null

    override fun execute(ctx: PipelineContext) {
        val run = ctx.run
        ctx.coleOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.cole },
                promptInputs =
                    mapOf(
                        "rawText" to (run.jiraTicketData ?: run.rawText),
                        "noraOutput" to ctx.noraOutput,
                        "dbSchema" to run.dbSchema,
                        "repoStructure" to ctx.repoStructure,
                    ),
                send = StageExecutor.viaCopilot(ctx),
                transform = AgentTextUtils::normaliseBadgeToFirstLine,
            )
    }
}

class MaxStage : PipelineStage {
    override val name: String = "max"
    override val rexFeedbackRank: Int? = null

    override fun execute(ctx: PipelineContext) {
        val run = ctx.run
        ctx.maxOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.max },
                promptInputs =
                    mapOf(
                        "noraOutput" to ctx.noraOutput,
                        "coleOutput" to ctx.coleOutput,
                        "repoStructure" to ctx.repoStructure,
                        "dbSchema" to run.dbSchema,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class FinnStage : PipelineStage {
    override val name: String = "finn"
    override val rexFeedbackRank: Int? = 1

    override fun execute(ctx: PipelineContext) {
        val run = ctx.run
        ctx.finnOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.finn },
                promptInputs =
                    mapOf(
                        "noraOutput" to ctx.noraOutput,
                        "coleOutput" to ctx.coleOutput,
                        "maxOutput" to ctx.maxOutput,
                        "repoStructure" to ctx.repoStructure,
                        "tessHandoff" to "",
                        "dbSchema" to run.dbSchema,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class FayeStage : PipelineStage {
    override val name: String = "faye"
    override val rexFeedbackRank: Int? = null

    override fun execute(ctx: PipelineContext) {
        ctx.fayeOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.faye },
                promptInputs =
                    mapOf(
                        "finnOutput" to ctx.finnOutput,
                        "coleOutput" to ctx.coleOutput,
                        "maxOutput" to ctx.maxOutput,
                        "noraOutput" to ctx.noraOutput,
                        "repoStructure" to ctx.repoStructure,
                        "mockupSection" to AgentContextHelpers.buildMockupSection(ctx.run.mockupImages),
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class VeraStage : PipelineStage {
    override val name: String = "vera"
    override val rexFeedbackRank: Int? = 6

    override fun execute(ctx: PipelineContext) {
        ctx.veraOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.vera },
                promptInputs =
                    mapOf(
                        "finnOutput" to ctx.finnOutput,
                        "fayeOutput" to ctx.fayeOutput,
                        "maxOutput" to ctx.maxOutput,
                        "noraOutput" to ctx.noraOutput,
                        "coleOutput" to ctx.coleOutput,
                        "repoStructure" to ctx.repoStructure,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class TessStage : PipelineStage {
    override val name: String = "tess"
    override val rexFeedbackRank: Int? = 2

    override fun execute(ctx: PipelineContext) {
        ctx.tessOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.tess },
                promptInputs =
                    mapOf(
                        "finnOutput" to ctx.finnOutput,
                        "coleOutput" to ctx.coleOutput,
                        "maxOutput" to ctx.maxOutput,
                        "noraOutput" to ctx.noraOutput,
                        "quinnHandoff" to "",
                        "sageHandoff" to "",
                        "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                        "dbSchema" to ctx.run.dbSchema,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class SydStage : PipelineStage {
    override val name: String = "syd"
    override val rexFeedbackRank: Int? = 3

    override fun execute(ctx: PipelineContext) {
        ctx.sydOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.syd },
                promptInputs =
                    mapOf(
                        "finnOutput" to ctx.finnOutput,
                        "tessOutput" to ctx.tessOutput,
                        "coleOutput" to ctx.coleOutput,
                        "maxOutput" to ctx.maxOutput,
                        "noraOutput" to ctx.noraOutput,
                        "quinnHandoff" to "",
                        "sageHandoff" to "",
                        "repoConfigured" to if (ctx.repoRoot != null) "true" else "false",
                        "dbSchema" to ctx.run.dbSchema,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class QuinnStage : PipelineStage {
    override val name: String = "quinn"
    override val rexFeedbackRank: Int? = 4

    override fun execute(ctx: PipelineContext) {
        ctx.quinnOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.quinn },
                promptInputs =
                    mapOf(
                        "finnOutput" to ctx.finnOutput,
                        "tessOutput" to (ctx.tessOutput + "\n\n---\n## Integration and System Tests (Syd)\n\n" + ctx.sydOutput),
                        "maxOutput" to ctx.maxOutput,
                        "noraOutput" to ctx.noraOutput,
                        "coleOutput" to ctx.coleOutput,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}

class SageStage : PipelineStage {
    override val name: String = "sage"
    override val rexFeedbackRank: Int? = 5

    override fun execute(ctx: PipelineContext) {
        ctx.sageOutput =
            StageExecutor.execute(
                stage = this,
                ctx = ctx,
                slot = { it.sage },
                promptInputs =
                    mapOf(
                        "finnOutput" to ctx.finnOutput,
                        "fayeOutput" to ctx.fayeOutput,
                        "maxOutput" to ctx.maxOutput,
                        "noraOutput" to ctx.noraOutput,
                        "coleOutput" to ctx.coleOutput,
                        "repoStructure" to ctx.repoStructure,
                        "quinnOutput" to ctx.quinnOutput,
                    ),
                send = StageExecutor.viaStream(ctx),
            )
    }
}
