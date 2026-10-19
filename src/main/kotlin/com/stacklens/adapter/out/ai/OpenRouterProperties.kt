package com.stacklens.adapter.out.ai

import java.net.http.HttpClient

data class OpenRouterProperties(
    val nonToolCapablePatterns: List<String> = DEFAULT_NON_TOOL_CAPABLE_PATTERNS,
    val httpVersion: HttpClient.Version = DEFAULT_HTTP_VERSION,
    val httpMaxRetries: Int = DEFAULT_HTTP_MAX_RETRIES,
    val httpRetryBackoffMs: Long = DEFAULT_HTTP_RETRY_BACKOFF_MS,
) {
    companion object {
        val DEFAULT_NON_TOOL_CAPABLE_PATTERNS: List<String> =
            listOf(
                "minimax",
                "gemma",
                "phi-3",
                "mixtral",
                "mistral-7b",
                "mistral-8b",
                "llama-3",
            )

        val DEFAULT_HTTP_VERSION: HttpClient.Version = HttpClient.Version.HTTP_1_1

        const val DEFAULT_HTTP_MAX_RETRIES: Int = 3

        const val DEFAULT_HTTP_RETRY_BACKOFF_MS: Long = 1_000L
    }
}
