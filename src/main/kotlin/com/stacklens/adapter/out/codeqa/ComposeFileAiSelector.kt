package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.AiChatPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.slf4j.LoggerFactory
import java.io.File

class ComposeFileAiSelector(
    private val copilotPort: AiChatPort,
    private val promptLoader: PromptReaderPort,
) {
    private val log = LoggerFactory.getLogger(ComposeFileAiSelector::class.java)

    fun select(
        files: List<File>,
        mrData: MrFetchPort.MrData,
        root: File,
    ): File {
        val listing = files.mapIndexed { i, f -> "${i + 1}. ${f.relativeTo(root).path}" }.joinToString("\n")
        val changed = mrData.changedFiles.take(30).joinToString("\n") { "- $it" }
        val prompt =
            promptLoader.get(
                "en",
                "codeqa.composeSelector",
                mapOf(
                    "listing" to listing,
                    "title" to mrData.title,
                    "sourceBranch" to mrData.sourceBranch,
                    "changedFiles" to changed,
                ),
            )
        return try {
            val raw = copilotPort.execute(copilotPort.resolveModel(), prompt, 30L).trim()
            val idx = Regex("\\d+").find(raw)?.value?.toIntOrNull()
            if (idx != null && idx in 1..files.size) files[idx - 1] else files.first()
        } catch (e: Exception) {
            log.warn("AI compose-file selection failed ({}), falling back to first candidate", e.message)
            files.first()
        }
    }
}
