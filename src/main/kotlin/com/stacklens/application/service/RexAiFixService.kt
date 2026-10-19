package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.domain.text.AgentTextUtils
import java.io.File

class RexAiFixService(
    private val copilot: CopilotStreamingPort,
    private val copilotConfig: CopilotConfigPort,
    private val processPort: AgentProcessPort,
    private val filePort: FileContentStore,
    private val loadPrompt: (String, Map<String, String>) -> String,
    private val fileAttributes: FileAttributes,
) {
    fun executeFixAttempt(
        run: AgentPipelineRun,
        sb: StringBuilder,
        repoPath: String,
        repoRoot: File,
        target: String,
        makeOutput: String,
        model: String,
        extractAffectedPaths: () -> List<String>,
        task: String,
        rerunTimeoutSeconds: Long?,
    ): Pair<Int, String> {
        val affectedPaths = extractAffectedPaths()
        val context = AgentContextHelpers.readFilesAsContext(affectedPaths, repoRoot, fileAttributes, filePort)
        val fixPrompt =
            loadPrompt(
                "rex",
                mapOf(
                    "task" to task,
                    "makeOutput" to makeOutput.take(8_000),
                    "currentFiles" to context,
                ),
            )
        val attemptLog = sb.toString()
        val fixOutput =
            copilot.executeWithStreamingPerLine(model, fixPrompt, copilotConfig.pipelineTimeoutSeconds, onPartialOutput = { partial ->
                run.rex.output = attemptLog + "\n\n🤖 KI generiert:\n$partial"
            })
        run.rex.output = sb.toString()
        writeGeneratedFiles(repoRoot, fixOutput)
        return if (rerunTimeoutSeconds == null) {
            processPort.runMakeTarget(repoPath, target)
        } else {
            processPort.runMakeTarget(repoPath, target, rerunTimeoutSeconds)
        }
    }

    fun extractMentionedPaths(
        output: String,
        repoRoot: File,
        regex: Regex,
    ): List<String> =
        regex
            .findAll(output)
            .mapNotNull { mr ->
                val raw = mr.value
                if (File(raw).isAbsolute) {
                    repoRoot.toPath().relativize(File(raw).toPath()).toString()
                } else {
                    raw
                }
            }.filter { !it.contains("vendor") }
            .distinct()
            .take(15)
            .toList()

    private fun writeGeneratedFiles(
        repoRoot: File,
        fixOutput: String,
    ) {
        for (pf in AgentTextUtils.parseAgentOutput(fixOutput)) {
            val t =
                try {
                    fileAttributes.canonicalFile(repoRoot.resolve(pf.path))
                } catch (_: Exception) {
                    continue
                }
            if (t.path.startsWith(repoRoot.path + File.separator)) {
                filePort.writeFile(t, pf.content)
            }
        }
    }
}
