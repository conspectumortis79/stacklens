package com.stacklens.adapter.out.ai

import com.stacklens.domain.model.AiConfig

class OpenAiCompatibleEndpoint(
    val label: String,
    val fallbackBaseUrl: String,
    val defaultModel: String,
    val nonToolCapablePatterns: List<String> = emptyList(),
    val extraHeaders: Map<String, String> = emptyMap(),
    val envVarNames: List<String> = emptyList(),
    val apiKeyOf: (AiConfig?) -> String? = { null },
    val baseUrlOf: (AiConfig?) -> String? = { null },
    val envReader: (String) -> String? = { name -> System.getenv(name) },
) {
    fun resolveApiKey(config: AiConfig?): String? =
        apiKeyOf(config)?.takeIf { it.isNotBlank() }
            ?: envVarNames
                .mapNotNull { name -> envReader(name)?.takeIf { it.isNotBlank() } }
                .firstOrNull()

    fun resolveBaseUrl(config: AiConfig?): String = OpenAiCompatibleUrl.normalize(baseUrlOf(config)?.takeIf { it.isNotBlank() } ?: fallbackBaseUrl)
}
