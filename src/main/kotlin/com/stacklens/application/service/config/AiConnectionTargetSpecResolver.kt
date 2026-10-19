package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import com.stacklens.application.port.model.AiConfigMasking
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig

interface AiConnectionTargetSpecResolver {
    val target: AiConnectionTestTarget

    fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ): ProviderSpec
}

private class GithubTokenSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.GITHUB_TOKEN

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.GithubToken(resolveSecret(request.githubToken, stored?.githubToken))
}

private class CopilotModelsSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.COPILOT_MODELS

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.CopilotModels(resolveSecret(request.githubToken, stored?.githubToken))
}

private class CopilotModelSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.COPILOT_MODEL

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.CopilotModel(
        requestedModel(request.model) ?: stored?.model?.takeIf { it.isNotBlank() },
        stored?.aiProvider ?: AiProvider.COPILOT.id,
    )
}

private class OllamaSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.OLLAMA

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.Ollama(
        resolveUrl(request.ollamaUrl, stored?.ollamaUrl, AiConnectionTestUseCase.OLLAMA_DEFAULT_URL),
    )
}

private class OpenAiSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.OPENAI

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.OpenAi(resolveSecret(request.openAiApiKey, stored?.openAiApiKey))
}

private class LmStudioSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.LMSTUDIO

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.LmStudio(
        resolveUrl(request.lmStudioUrl, stored?.lmStudioUrl, AiConnectionTestUseCase.LMSTUDIO_DEFAULT_URL),
    )
}

private class OpenRouterSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.OPENROUTER

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.OpenRouter(resolveSecret(request.openRouterApiKey, stored?.openRouterApiKey))
}

private class ClaudeCodeSpecResolver : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.CLAUDE_CODE

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.ClaudeCode(
        resolveSecret(request.claudeCodeApiKey, stored?.claudeCodeApiKey),
        requestedModel(request.model) ?: storedModelFor(stored, AiProvider.CLAUDECODE.id),
    )
}

private class CompatibleSpecResolver(
    private val aiDefaults: AiDefaultsPort,
) : AiConnectionTargetSpecResolver {
    override val target = AiConnectionTestTarget.COMPATIBLE

    override fun resolve(
        request: AiConnectionTestRequest,
        stored: AiConfig?,
    ) = ProviderSpec.Compatible(
        resolveSecret(request.compatibleApiKey, stored?.compatibleApiKey),
        resolveTrimmedUrl(request.compatibleBaseUrl, stored?.compatibleBaseUrl, aiDefaults.compatibleDefaultBaseUrl()),
        requestedModel(request.model) ?: storedModelFor(stored, AiProvider.COMPATIBLE.id),
    )
}

fun defaultAiConnectionTargetSpecResolvers(aiDefaults: AiDefaultsPort): List<AiConnectionTargetSpecResolver> =
    listOf(
        GithubTokenSpecResolver(),
        CopilotModelsSpecResolver(),
        CopilotModelSpecResolver(),
        OllamaSpecResolver(),
        OpenAiSpecResolver(),
        LmStudioSpecResolver(),
        OpenRouterSpecResolver(),
        ClaudeCodeSpecResolver(),
        CompatibleSpecResolver(aiDefaults),
    )

private fun requestedModel(model: String?): String? = model?.takeIf { it.isNotBlank() }

private fun storedModelFor(
    stored: AiConfig?,
    providerId: String,
): String? = stored?.model.takeIf { stored?.aiProvider == providerId }?.takeIf { it.isNotBlank() }

private fun resolveSecret(
    submitted: String?,
    stored: String?,
): String? {
    if (!submitted.isNullOrBlank() && submitted != AiConfigMasking.MASKED_SECRET) return submitted
    return stored?.takeIf { it.isNotBlank() }
}

private fun resolveTrimmedUrl(
    submitted: String?,
    stored: String?,
    fallback: String,
): String = submitted?.trim()?.takeIf { it.isNotEmpty() } ?: stored?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

private fun resolveUrl(
    submitted: String?,
    stored: String?,
    fallback: String,
): String {
    if (!submitted.isNullOrEmpty()) return submitted
    if (!stored.isNullOrEmpty()) return stored
    return fallback
}
