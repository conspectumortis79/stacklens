package com.stacklens.application.port.inbound.config

enum class AiConnectionTestTarget {
    GITHUB_TOKEN,
    COPILOT_MODELS,
    COPILOT_MODEL,
    OLLAMA,
    OPENAI,
    LMSTUDIO,
    OPENROUTER,
    CLAUDE_CODE,
    COMPATIBLE,
}

data class AiConnectionTestRequest(
    val model: String? = null,
    val githubToken: String? = null,
    val ollamaUrl: String? = null,
    val lmStudioUrl: String? = null,
    val openAiApiKey: String? = null,
    val openRouterApiKey: String? = null,
    val claudeCodeApiKey: String? = null,
    val compatibleApiKey: String? = null,
    val compatibleBaseUrl: String? = null,
)

interface AiConnectionTestUseCase {
    fun testConnection(
        target: AiConnectionTestTarget,
        request: AiConnectionTestRequest,
    ): Map<String, Any>

    companion object {
        const val OLLAMA_DEFAULT_URL: String = "http://localhost:11434"
        const val LMSTUDIO_DEFAULT_URL: String = "http://localhost:1234"
    }
}
