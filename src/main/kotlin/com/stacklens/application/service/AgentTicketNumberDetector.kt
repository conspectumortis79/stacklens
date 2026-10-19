package com.stacklens.application.service

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.service.AgentPromptLoader
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.text.AgentTextUtils
import org.slf4j.LoggerFactory

@ApplicationService
class AgentTicketNumberDetector(
    private val copilot: CopilotStreamingPort,
    private val stackLensConfig: StackLensConfigPort,
    private val classpathResource: ClasspathResourcePort,
    private val fileAttributes: FileAttributes,
    private val fileContentStore: FileContentStore,
) {
    private val log = LoggerFactory.getLogger(AgentTicketNumberDetector::class.java)

    fun detect(
        model: String,
        rawText: String,
    ): String? =
        try {
            val prompt =
                AgentPromptLoader.load(
                    stackLensConfig.dataDir,
                    "skills/ticket-detect",
                    mapOf("rawText" to rawText),
                    classpathResource,
                    fileAttributes,
                    fileContentStore,
                )
            val raw = copilot.executeWithStreamingPerLine(model, prompt, 60L, onPartialOutput = { })
            AgentTextUtils.sanitiseDetectedTicketKey(raw)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            log.warn("AI ticket detection failed, falling back to word-based slug: {}", e.message)
            null
        }
}
