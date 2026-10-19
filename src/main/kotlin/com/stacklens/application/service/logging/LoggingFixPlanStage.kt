package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.file.FileListingReadWritePort
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
class LoggingFixPlanStage(
    private val copilot: CopilotStreamingPort,
    private val promptLoader: PromptLoaderPort,
    private val markers: LoggingFixMarkers,
    private val fileSystem: FileListingReadWritePort,
) : LoggingFixStage {
    override fun execute(ctx: LoggingFixStageContext) {
        if (ctx.diagnosisIncomplete) {
            val marker = markers.stageSkippedNoDiagnosisMarker(STAGE_NAME, ctx.language)
            ctx.planOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        if (ctx.repoRoot == null) {
            val marker = markers.stageSkippedNoRepositoryMarker(STAGE_NAME, ctx.language)
            ctx.planOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        val repoSnapshot = scanRepo(ctx.repoRoot)
        val prompt =
            promptLoader.get(
                lang = ctx.language,
                key = PROMPT_KEY,
                vars =
                    mapOf(
                        "diagnose" to ctx.diagnoseOutput.toString(),
                        "categories" to ctx.selectedCategories.joinToString(", "),
                        "repo_snapshot" to repoSnapshot,
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
            ctx.planOutput.append(marker)
            ctx.onPartial(STAGE_NAME, marker)
            return
        }
        ctx.planOutput.append(output)
    }

    private fun scanRepo(root: File): String {
        val maxBytes = 64 * 1024
        val sb = StringBuilder()
        appendExcerpt(sb, root, "README.md", "## README.md (excerpt)", maxBytes)
        appendExcerpt(sb, root, "build.gradle.kts", "## build.gradle.kts (excerpt)", maxBytes)
        appendPackageList(sb, root, "src/main/kotlin", "## Kotlin packages:", maxBytes, 40)
        appendPackageList(sb, root, "src/test/kotlin", "## Test packages:", maxBytes, 40)
        val systemTestsFound = appendPackageList(sb, root, "src/test/systemTest/kotlin", "## Systemtest packages:", maxBytes, 20)
        if (!systemTestsFound) {
            sb.appendLine()
            sb.appendLine("## Systemtest-Verzeichnis: NICHT GEFUNDEN (src/test/systemTest/kotlin)")
        }
        return sb.toString().take(maxBytes)
    }

    private fun appendExcerpt(
        sb: StringBuilder,
        root: File,
        relativePath: String,
        heading: String,
        maxBytes: Int,
    ) {
        val file = File(root, relativePath)
        if (!fileSystem.exists(file) || fileSystem.size(file) >= maxBytes) return
        sb.appendLine(heading)
        sb.appendLine(fileSystem.readText(file).take(maxBytes / 2))
        sb.appendLine()
    }

    private fun appendPackageList(
        sb: StringBuilder,
        root: File,
        relativePath: String,
        heading: String,
        maxBytes: Int,
        limit: Int,
    ): Boolean {
        val dir = File(root, relativePath)
        if (!fileSystem.exists(dir)) return false
        sb.appendLine()
        sb.appendLine(heading)
        fileSystem
            .listDirectories(root = dir, relativeTo = root, maxDepth = 6, limit = limit)
            .forEach { sb.appendLine("- $it") }
        return true
    }

    companion object {
        const val STAGE_NAME = "plan"
        const val PROMPT_KEY = "logging.fix_plan"
        const val QUALIFIER = "loggingFixPlanStage"
    }
}
