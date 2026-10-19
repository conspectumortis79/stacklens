package com.stacklens.domain.enums

enum class AiProvider(
    val id: String,
    val strategy: ProviderStrategy,
) {
    COPILOT(ProviderStrategies.COPILOT_ID, ProviderStrategies.COPILOT),
    OLLAMA(ProviderStrategies.OLLAMA_ID, ProviderStrategies.OLLAMA),
    LMSTUDIO(ProviderStrategies.LMSTUDIO_ID, ProviderStrategies.LMSTUDIO),
    OPENAI(ProviderStrategies.OPENAI_ID, ProviderStrategies.OPENAI),
    OPENROUTER(ProviderStrategies.OPENROUTER_ID, ProviderStrategies.OPENROUTER),
    CLAUDECODE(ProviderStrategies.CLAUDECODE_ID, ProviderStrategies.CLAUDECODE),
    COMPATIBLE(ProviderStrategies.COMPATIBLE_ID, ProviderStrategies.COMPATIBLE),
    ;

    val defaultModel: String get() = strategy.defaultModel

    val requiresToken: Boolean get() = strategy.requiresToken

    companion object {
        fun fromId(id: String?): AiProvider? {
            val normalized = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == normalized }
        }

        const val LEGACY_COPILOT_CLI_ID: String = "copilot-cli"

        fun isCopilotFamily(id: String?): Boolean {
            val normalized = id?.trim()?.lowercase().orEmpty()
            return normalized.isEmpty() ||
                normalized == COPILOT.id ||
                normalized == LEGACY_COPILOT_CLI_ID
        }
    }
}
