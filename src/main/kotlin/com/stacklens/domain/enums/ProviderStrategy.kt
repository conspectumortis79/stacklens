package com.stacklens.domain.enums

import com.stacklens.domain.model.AiConfig

interface ProviderStrategy {
    val id: String
    val defaultModel: String
    val requiresToken: Boolean

    fun resolveModel(
        dtoModel: String?,
        existing: AiConfig,
    ): String = resolveModelStatic(dtoModel, existing, defaultModel)

    fun applyRequestFields(
        config: AiConfig,
        openAiReasoningEffort: String?,
    )

    fun existingForModelResolution(
        config: AiConfig,
        dtoModel: String?,
    ): AiConfig

    companion object {
        fun resolveModelStatic(
            dtoModel: String?,
            existing: AiConfig,
            defaultModel: String,
        ): String {
            dtoModel?.takeIf { it.isNotBlank() }?.let { return it }
            existing.model?.takeIf { it.isNotBlank() }?.let { return it }
            return defaultModel
        }
    }
}

interface LiveModelResolvingProvider : ProviderStrategy {
    fun resolveModel(
        dtoModel: String?,
        dtoLmStudioUrl: String?,
        existing: AiConfig,
        lmStudioLiveModel: String?,
    ): String

    fun resolveLiveModel(
        provider: String,
        dtoModel: String?,
        dtoLmStudioUrl: String?,
        existing: AiConfig,
        resolve: (String?, String?, AiConfig) -> String?,
    ): String?
}

object ProviderStrategies {
    const val COPILOT_ID = "copilot"
    const val OLLAMA_ID = "ollama"
    const val LMSTUDIO_ID = "lmstudio"
    const val OPENAI_ID = "openai"
    const val OPENROUTER_ID = "openrouter"
    const val CLAUDECODE_ID = "claudecode"
    const val COMPATIBLE_ID = "compatible"

    const val COMPATIBLE_DEFAULT_MODEL: String = "qwen3.8-flash"

    val COPILOT: ProviderStrategy =
        BaseProviderStrategy(id = COPILOT_ID, defaultModel = "gpt-5.4-mini", requiresToken = true)
    val OLLAMA: ProviderStrategy =
        BaseProviderStrategy(id = OLLAMA_ID, defaultModel = "gpt-5.4-mini", requiresToken = false)
    val OPENAI: ProviderStrategy =
        BaseProviderStrategy(
            id = OPENAI_ID,
            defaultModel = "gpt-5.2",
            requiresToken = true,
            alwaysAppliesReasoningEffort = true,
        )
    val OPENROUTER: ProviderStrategy =
        BaseProviderStrategy(id = OPENROUTER_ID, defaultModel = "openai/gpt-4.1", requiresToken = true)
    val CLAUDECODE: ProviderStrategy =
        BaseProviderStrategy(id = CLAUDECODE_ID, defaultModel = "claude-sonnet-4-5", requiresToken = true)
    val COMPATIBLE: ProviderStrategy =
        BaseProviderStrategy(id = COMPATIBLE_ID, defaultModel = COMPATIBLE_DEFAULT_MODEL, requiresToken = true)

    val LMSTUDIO: LiveModelResolvingProvider = LmStudioProviderStrategy

    private class BaseProviderStrategy(
        override val id: String,
        override val defaultModel: String,
        override val requiresToken: Boolean,
        private val alwaysAppliesReasoningEffort: Boolean = false,
    ) : ProviderStrategy {
        override fun applyRequestFields(
            config: AiConfig,
            openAiReasoningEffort: String?,
        ) {
            if (alwaysAppliesReasoningEffort || openAiReasoningEffort != null) {
                config.openAiReasoningEffort = OpenAiReasoningEffort.normalize(openAiReasoningEffort)
            }
        }

        override fun existingForModelResolution(
            config: AiConfig,
            dtoModel: String?,
        ): AiConfig = if (dtoModel.isNullOrBlank()) config.copy(model = null) else config
    }

    private object LmStudioProviderStrategy : LiveModelResolvingProvider {
        override val id: String = LMSTUDIO_ID
        override val defaultModel: String = "local-model"
        override val requiresToken: Boolean = false

        override fun applyRequestFields(
            config: AiConfig,
            openAiReasoningEffort: String?,
        ) {
            if (openAiReasoningEffort != null) {
                config.openAiReasoningEffort = OpenAiReasoningEffort.normalize(openAiReasoningEffort)
            }
        }

        override fun existingForModelResolution(
            config: AiConfig,
            dtoModel: String?,
        ): AiConfig = if (dtoModel.isNullOrBlank()) config.copy(model = null) else config

        override fun resolveLiveModel(
            provider: String,
            dtoModel: String?,
            dtoLmStudioUrl: String?,
            existing: AiConfig,
            resolve: (String?, String?, AiConfig) -> String?,
        ): String? = if (provider == id) resolve(dtoModel, dtoLmStudioUrl, existing) else null

        override fun resolveModel(
            dtoModel: String?,
            dtoLmStudioUrl: String?,
            existing: AiConfig,
            lmStudioLiveModel: String?,
        ): String {
            dtoModel?.takeIf { it.isNotBlank() }?.let { return it }
            if (existing.aiProvider == id) {
                existing.model?.takeIf { it.isNotBlank() }?.let { return it }
                return lmStudioLiveModel?.takeIf { it.isNotBlank() } ?: defaultModel
            }
            if (existing.model.isNullOrBlank()) {
                return lmStudioLiveModel?.takeIf { it.isNotBlank() } ?: defaultModel
            }
            return defaultModel
        }
    }
}
