package com.stacklens.application.port.out.ai

import com.stacklens.application.port.model.ConnectionFailureKind

interface AiConnectionTestPort {
    sealed class TestResult {
        abstract val payload: Map<String, Any>

        data class Success(
            override val payload: Map<String, Any>,
        ) : TestResult()

        data class Failure(
            val kind: ConnectionFailureKind,
            override val payload: Map<String, Any>,
        ) : TestResult()
    }

    fun testConnection(spec: ProviderSpec): TestResult
}

sealed class ProviderSpec {
    data class GithubToken(
        val token: String?,
    ) : ProviderSpec()

    data class CopilotModels(
        val token: String?,
    ) : ProviderSpec()

    data class CopilotModel(
        val model: String?,
        val savedProvider: String,
    ) : ProviderSpec()

    data class Ollama(
        val baseUrl: String,
    ) : ProviderSpec()

    data class OpenAi(
        val apiKey: String?,
    ) : ProviderSpec()

    data class LmStudio(
        val baseUrl: String,
    ) : ProviderSpec()

    data class OpenRouter(
        val apiKey: String?,
    ) : ProviderSpec()

    data class ClaudeCode(
        val apiKey: String?,
        val model: String? = null,
    ) : ProviderSpec()

    data class Compatible(
        val apiKey: String?,
        val baseUrl: String,
        val model: String?,
    ) : ProviderSpec()
}
