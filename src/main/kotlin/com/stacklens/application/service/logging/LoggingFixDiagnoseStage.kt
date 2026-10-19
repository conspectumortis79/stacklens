package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.pipeline.LoggingInputHeuristic
import org.slf4j.LoggerFactory

@ApplicationService
class LoggingFixDiagnoseStage(
    private val copilot: CopilotStreamingPort,
    private val promptLoader: PromptLoaderPort,
    private val markers: LoggingFixMarkers,
) : LoggingFixStage {
    private val log = LoggerFactory.getLogger(LoggingFixDiagnoseStage::class.java)

    override fun execute(ctx: LoggingFixStageContext) {
        val verdict = LoggingInputHeuristic.classify(ctx.logText)
        if (verdict.tooThin) {
            log.info(
                "[LoggingFix diagnose] short-circuiting with skip-marker (lines={}, stackTrace={}, isoDate={})",
                verdict.nonBlankLineCount,
                verdict.hasStackTraceMarker,
                verdict.hasIsoDate,
            )
            ctx.diagnosisIncomplete = true
            val marker = markers.skipMarker(ctx.language)
            ctx.onPartial(STAGE_NAME, marker)
            ctx.diagnoseOutput.append(marker)
            return
        }

        val categories =
            if (ctx.selectedCategories.isEmpty()) {
                markers.categoriesAll(ctx.language)
            } else {
                ctx.selectedCategories.joinToString(", ")
            }
        val prompt =
            promptLoader.get(
                lang = ctx.language,
                key = PROMPT_KEY,
                vars =
                    mapOf(
                        "categories" to categories,
                        "log_text" to ctx.logText.take(MAX_LOG_CHARS),
                    ),
            )
        val output =
            copilot.executeWithStreamingPerLine(
                model = copilot.resolveModel(),
                prompt = prompt,
                onPartialOutput = { chunk -> ctx.onPartial(STAGE_NAME, chunk) },
                isCancelled = ctx.isCancelled,
            )
        if (output.isBlank()) {
            val marker = markers.emptyResultMarker(STAGE_NAME, ctx.language)
            ctx.onPartial(STAGE_NAME, marker)
            ctx.diagnoseOutput.append(marker)
            ctx.diagnosisIncomplete = true
            return
        }
        ctx.diagnoseOutput.append(output)
        if (isDiagnosisNotPossibleMarker(output)) {
            ctx.diagnosisIncomplete = true
        }
    }

    internal fun isDiagnosisNotPossibleMarker(output: String): Boolean = markers.isSkipMarker(output)

    companion object {
        const val STAGE_NAME = "diagnose"
        const val PROMPT_KEY = "logging.fix_diagnose"
        const val QUALIFIER = "loggingFixDiagnoseStage"
        private const val MAX_LOG_CHARS = 20_000
    }
}
