package com.stacklens.application.port.inbound.prompt

enum class AgentPromptSaveOutcome {
    SAVED,
    UNKNOWN_AGENT,
    BLANK_CONTENT,
}

data class AgentPromptSummary(
    val name: String,
    val label: String,
)

data class AgentPromptContent(
    val name: String,
    val content: String?,
    val isCustom: Boolean,
    val defaultContent: String,
)

interface AgentPromptUseCase {
    fun listAgents(): List<AgentPromptSummary>

    fun getAgent(name: String): AgentPromptContent?

    fun saveAgent(
        name: String,
        content: String?,
    ): AgentPromptSaveOutcome

    fun resetAgent(name: String): AgentPromptContent?
}
