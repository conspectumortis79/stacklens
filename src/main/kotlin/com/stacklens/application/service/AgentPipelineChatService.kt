package com.stacklens.application.service

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class AgentPipelineChatService(
    private val registry: CopilotPipelineRegistry,
    private val copilot: CopilotStreamingPort,
    private val i18n: I18nPort,
) {
    fun chat(
        id: Long,
        userMessage: String,
        onChunk: (String) -> Unit,
    ): String {
        val run = registry.get(id) ?: return i18n.t("copilot_pipeline.be_run_not_found", id)
        val model = copilot.resolveModel()
        val context = ChatContextBuilder.build(run)
        val fullPrompt = "$context\n\n## User Message\n$userMessage"
        return copilot.executeWithStreamingPerLine(model, fullPrompt, 300L, null, null, onChunk)
    }
}
