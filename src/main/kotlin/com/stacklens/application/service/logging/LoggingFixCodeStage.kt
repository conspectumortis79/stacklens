package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class LoggingFixCodeStage(
    private val copilot: CopilotStreamingPort,
    private val promptLoader: PromptLoaderPort,
    private val markers: LoggingFixMarkers,
) : LoggingFixStage {
    override fun execute(ctx: LoggingFixStageContext) {
        if (ctx.diagnosisIncomplete) {
            val marker = markers.stageSkippedNoDiagnosisMarker(STAGE_NAME, ctx.language)
            ctx.codeOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        if (ctx.repoRoot == null) {
            val marker = markers.stageSkippedNoRepositoryMarker(STAGE_NAME, ctx.language)
            ctx.codeOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        val prompt =
            promptLoader.get(
                lang = ctx.language,
                key = PROMPT_KEY,
                vars =
                    mapOf(
                        "diagnose" to ctx.diagnoseOutput.toString(),
                        "plan" to ctx.planOutput.toString(),
                        "categories" to ctx.selectedCategories.joinToString(", "),
                    ),
            )
        val output =
            copilot.executeWithStreamingPerLine(
                model = copilot.resolveModel(),
                prompt = prompt,
                repoDir = ctx.repoRoot,
                onPartialOutput = { chunk -> ctx.onPartial(STAGE_NAME, chunk) },
                isCancelled = ctx.isCancelled,
            )
        if (output.isBlank()) {
            val marker = markers.emptyResultMarker(STAGE_NAME, ctx.language)
            ctx.codeOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        ctx.codeOutput.append(output)
        ctx.proposedFiles += ProposedFileParser.parse(output)
    }

    companion object {
        const val STAGE_NAME = "code"
        const val PROMPT_KEY = "logging.fix_code"
        const val QUALIFIER = "loggingFixCodeStage"
    }
}
