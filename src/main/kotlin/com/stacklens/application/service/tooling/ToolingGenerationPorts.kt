package com.stacklens.application.service.tooling

interface ToolingGenerationRunner {
    fun runGeneration(toolId: Long)
}

interface ToolingPromptBuilder {
    fun buildCodeGenerationPrompt(
        toolName: String,
        pageContent: String,
    ): String
}
