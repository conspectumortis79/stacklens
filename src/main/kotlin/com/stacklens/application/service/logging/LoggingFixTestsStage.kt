package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.file.FileListingReadWritePort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
class LoggingFixTestsStage(
    private val copilot: CopilotStreamingPort,
    private val promptLoader: PromptLoaderPort,
    private val markers: LoggingFixMarkers,
    private val fileSystem: FileListingReadWritePort,
) : LoggingFixStage {
    override fun execute(ctx: LoggingFixStageContext) {
        if (ctx.diagnosisIncomplete) {
            val marker = markers.stageSkippedNoDiagnosisMarker(STAGE_NAME, ctx.language)
            ctx.testsOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        if (ctx.repoRoot == null) {
            val marker = markers.stageSkippedNoRepositoryMarker(STAGE_NAME, ctx.language)
            ctx.testsOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        val existingSystemTests = listSystemTestSnippets(ctx.repoRoot)
        val prompt =
            promptLoader.get(
                lang = ctx.language,
                key = PROMPT_KEY,
                vars =
                    mapOf(
                        "diagnose" to ctx.diagnoseOutput.toString(),
                        "plan" to ctx.planOutput.toString(),
                        "code" to ctx.codeOutput.toString(),
                        "existing_system_tests" to existingSystemTests,
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
            ctx.testsOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        ctx.testsOutput.append(output)
        ctx.proposedFiles += ProposedFileParser.parse(output)
    }

    private fun listSystemTestSnippets(root: File): String {
        val sysTestRoot = File(root, "src/test/systemTest/kotlin")
        if (!fileSystem.exists(sysTestRoot)) {
            return "(Kein src/test/systemTest/kotlin im Repo gefunden — KI prüft selbst, ob ein Systemtest angelegt werden sollte.)"
        }
        val sb = StringBuilder("Bekannte Systemtest-Dateien (Auszug):\n")
        fileSystem
            .listFiles(root = sysTestRoot, relativeTo = root, extension = "kt", limit = 15)
            .forEach { sb.appendLine("- $it") }
        val first = fileSystem.firstFile(root = sysTestRoot, extension = "kt")
        if (first != null && fileSystem.size(first) < 4 * 1024) {
            sb.appendLine()
            sb.appendLine("Erster Systemtest (excerpt, für Tag/Konvention-Erkennung):")
            sb.appendLine(fileSystem.readText(first).take(2_000))
        }
        return sb.toString()
    }

    companion object {
        const val STAGE_NAME = "tests"
        const val PROMPT_KEY = "logging.fix_test"
        const val QUALIFIER = "loggingFixTestsStage"
    }
}
