package com.stacklens.application.port.out.ai

interface AiUsageContextPort {
    fun setContext(
        kind: String,
        analysisId: Long,
    )

    fun clearContext()

    fun <T> withContext(
        kind: String,
        analysisId: Long,
        block: () -> T,
    ): T

    fun addFilterSavings(tokens: Long)

    fun setModelOverride(model: String)
}
