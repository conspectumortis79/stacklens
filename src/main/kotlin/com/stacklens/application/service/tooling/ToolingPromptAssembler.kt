package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class ToolingPromptAssembler(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
) : ToolingPromptBuilder {
    override fun buildCodeGenerationPrompt(
        toolName: String,
        pageContent: String,
    ): String =
        promptLoader.get(
            copilotCliService.resolveLanguage(),
            "tooling.codeGeneration",
            mapOf("toolName" to toolName, "pageContent" to pageContent.take(8_000)),
        )
}
