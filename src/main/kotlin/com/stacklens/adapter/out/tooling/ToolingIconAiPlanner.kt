package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ToolingIconAiPlanner(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
) {
    private val log = LoggerFactory.getLogger(ToolingIconAiPlanner::class.java)

    fun determineIcon(
        toolName: String,
        generatedCode: String,
    ): String? =
        try {
            val prompt =
                promptLoader.get(
                    copilotCliService.resolveLanguage(),
                    "tooling.iconSelection",
                    mapOf("toolName" to toolName, "codeExcerpt" to generatedCode.take(400)),
                )
            val response = copilotCliService.execute(copilotCliService.resolveModel(), prompt, 30L)
            Regex("bi-[a-z0-9-]+").find(response.trim())?.value
        } catch (e: Exception) {
            log.warn("[Tooling] Icon lookup failed for '{}': {}", toolName, e.message)
            null
        }
}
