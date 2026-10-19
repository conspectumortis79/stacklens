package com.stacklens.adapter.out.ai

internal object OpenAiCompatibleUrl {
    fun normalize(raw: String?): String {
        val trimmed = raw?.trim()?.trimEnd('/') ?: ""
        if (trimmed.isEmpty()) return ""
        return if (SCHEME in trimmed) trimmed else "$DEFAULT_SCHEME$trimmed"
    }

    fun models(baseUrl: String): String = "$baseUrl/models"

    fun chatCompletions(baseUrl: String): String = "$baseUrl/chat/completions"

    private const val SCHEME = "://"

    private const val DEFAULT_SCHEME = "https://"
}
