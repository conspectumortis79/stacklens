package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.service.AgentContextHelpers
import com.stacklens.domain.text.AgentTextUtils
import org.slf4j.LoggerFactory

class NoraStage : PipelineStage {
    override val name: String = "nora"
    override val rexFeedbackRank: Int? = null

    private val log = LoggerFactory.getLogger(NoraStage::class.java)

    override fun execute(ctx: PipelineContext) {
        val run = ctx.run
        run.currentStage = name
        run.nora.status = StageStatus.RUNNING
        log.info("[Pipeline {}] Stage {} Nora start", run.id, name)

        val noraInput =
            ctx.lenaOutput
                ?.lines()
                ?.dropLastWhile { it.isBlank() || it.trimStart().startsWith("> ✅ Lena complete") }
                ?.joinToString("\n")
                ?.trim() ?: run.rawText
        val noraMockupSection = AgentContextHelpers.buildMockupSection(run.mockupImages)
        val noraDbSection = if (run.dbSchema.isNotBlank()) "## Database schema\n\n${run.dbSchema}" else ""
        val prompt =
            ctx.loadAgentPrompt(
                "nora",
                mapOf(
                    "rawText" to noraInput,
                    "repoStructure" to ctx.repoStructure,
                    "dbSchema" to noraDbSection,
                    "mockupSection" to noraMockupSection,
                ),
            )
        val rawOutput =
            ctx.stream(ctx.model, prompt, null) { partial -> run.nora.output = partial }
        val output =
            rawOutput
                .lines()
                .dropLastWhile { it.isBlank() || it.trimStart().startsWith("> ✅ Nora complete") }
                .joinToString("\n")
                .trim()
        run.nora.output = output
        run.nora.status = StageStatus.DONE

        val noraSlug = AgentTextUtils.extractSlugFromNoraSummary(output)
        val ticketKey = run.detectedTicketKey?.takeIf { it.isNotBlank() }
        run.featureSlug =
            when {
                ticketKey != null -> ticketKey
                noraSlug.isNotBlank() -> noraSlug
                else -> run.featureSlug
            }

        ctx.lenaOutput?.let { ctx.writeStageOutput(run, "lena", it) }
        ctx.writeStageOutput(run, "nora", output)
        log.info("[Pipeline {}] Stage {} Nora done ({} chars), slug={}", run.id, name, output.length, run.featureSlug)

        ctx.noraOutput = output
    }
}

internal var PipelineContext.noraOutput: String
    get() = outputs.of("nora")
    set(value) {
        outputs.record("nora", value)
    }
