package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiConfigDto
import com.stacklens.application.port.model.AiConfigMasking
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.enums.OpenAiReasoningEffort
import com.stacklens.domain.model.AiConfig
import org.springframework.stereotype.Component

@Component
class AiConfigDtoMapper {
    fun toDto(cfg: AiConfig?): AiConfigDto {
        val ghToken = cfg?.githubToken
        val oaiKey = cfg?.openAiApiKey
        val orKey = cfg?.openRouterApiKey
        val ccKey = cfg?.claudeCodeApiKey
        val compatibleKey = cfg?.compatibleApiKey
        return AiConfigDto(
            githubToken = if (ghToken.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            githubTokenSet = !ghToken.isNullOrBlank(),
            openAiApiKey = if (oaiKey.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            openAiApiKeySet = !oaiKey.isNullOrBlank(),
            openAiReasoningEffort = normalizeOpenAiReasoningEffort(cfg?.openAiReasoningEffort),
            model = cfg?.model ?: "",
            availableModels = cfg?.availableModels?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            aiProvider = cfg?.aiProvider ?: AiProvider.COPILOT.id,
            ollamaUrl = cfg?.ollamaUrl ?: "",
            ollamaNumCtx = cfg?.ollamaNumCtx ?: 32768,
            lmStudioUrl = cfg?.lmStudioUrl ?: "",
            openRouterApiKey = if (orKey == null || orKey.isEmpty()) "" else AiConfigMasking.MASKED_SECRET,
            openRouterApiKeySet = orKey != null && orKey.isNotEmpty(),
            claudeCodeApiKey = if (ccKey.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            claudeCodeApiKeySet = !ccKey.isNullOrBlank(),
            compatibleApiKey = if (compatibleKey.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            compatibleApiKeySet = !compatibleKey.isNullOrBlank(),
            compatibleBaseUrl = cfg?.compatibleBaseUrl ?: "",
        )
    }

    fun toDto(
        existing: AiConfig,
        resolvedModel: String,
    ): AiConfigDto {
        val orKey = existing.openRouterApiKey
        val ccKey = existing.claudeCodeApiKey
        val compatibleKey = existing.compatibleApiKey
        return AiConfigDto(
            githubToken = if (existing.githubToken.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            githubTokenSet = !existing.githubToken.isNullOrBlank(),
            openAiApiKey = if (existing.openAiApiKey.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            openAiApiKeySet = !existing.openAiApiKey.isNullOrBlank(),
            openAiReasoningEffort = normalizeOpenAiReasoningEffort(existing.openAiReasoningEffort),
            model = resolvedModel,
            availableModels = emptyList(),
            aiProvider = existing.aiProvider,
            ollamaUrl = existing.ollamaUrl ?: "",
            ollamaNumCtx = existing.ollamaNumCtx,
            lmStudioUrl = existing.lmStudioUrl ?: "",
            openRouterApiKey = if (orKey == null || orKey.isEmpty()) "" else AiConfigMasking.MASKED_SECRET,
            openRouterApiKeySet = orKey != null && orKey.isNotEmpty(),
            claudeCodeApiKey = if (ccKey.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            claudeCodeApiKeySet = !ccKey.isNullOrBlank(),
            compatibleApiKey = if (compatibleKey.isNullOrBlank()) "" else AiConfigMasking.MASKED_SECRET,
            compatibleApiKeySet = !compatibleKey.isNullOrBlank(),
            compatibleBaseUrl = existing.compatibleBaseUrl ?: "",
        )
    }

    internal fun normalizeOpenAiReasoningEffort(value: String?): String = OpenAiReasoningEffort.normalize(value)
}
