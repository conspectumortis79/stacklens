package com.stacklens.application.service

import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.prompt.PromptReaderPort

internal object KiChatServiceTestSupport {
    fun buildPromptLoader(
        promptLoader: PromptReaderPort,
        appKnowledge: AppKnowledgePort,
    ): KiChatPromptBuilder = KiChatPromptBuilder(promptLoader, appKnowledge)

    fun buildNoopPreflight(): McpChatPreflight = McpChatPreflight { _, _ -> "" }
}
