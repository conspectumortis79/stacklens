package com.stacklens.application.port.out.ai

interface AiUsageExtractor {
    fun extract(
        responseBody: String,
        providerLabel: String,
    ): ExtractedUsage?
}

data class ExtractedUsage(
    val promptTokens: Long,
    val completionTokens: Long,
)
