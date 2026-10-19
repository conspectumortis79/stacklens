package com.stacklens.adapter.out.ai

import java.util.concurrent.ConcurrentHashMap

class ClaudeThinkingPolicy(
    private val maxTokens: Int,
    private val maxTokensCap: Int,
    private val thinkingMode: String,
    private val thinkingEffort: String,
) {
    private val degradedModels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val tokensCap: Int
        get() = maxTokensCap.coerceAtLeast(ClaudeCodeAdapter.MIN_TOKENS_FLOOR)

    fun effectiveMaxTokens(): Int = maxTokens.coerceIn(ClaudeCodeAdapter.MIN_TOKENS_FLOOR, tokensCap)

    fun effectiveThinkingMode(model: String): String =
        when {
            model.isNotBlank() && degradedModels.contains(model) -> ClaudeCodeAdapter.THINKING_MODE_NONE
            thinkingMode.isBlank() -> ClaudeCodeAdapter.THINKING_MODE_NONE
            else -> thinkingMode.trim().lowercase()
        }

    fun thinkingFields(model: String): Map<String, Any> =
        when (effectiveThinkingMode(model)) {
            ClaudeCodeAdapter.THINKING_MODE_ADAPTIVE ->
                mapOf(
                    "thinking" to mapOf("type" to ClaudeCodeAdapter.THINKING_MODE_ADAPTIVE),
                    "output_config" to mapOf("effort" to resolvedThinkingEffort()),
                )
            ClaudeCodeAdapter.THINKING_MODE_DISABLED -> mapOf("thinking" to mapOf("type" to ClaudeCodeAdapter.THINKING_MODE_DISABLED))
            else -> emptyMap()
        }

    fun resolvedThinkingEffort(): String =
        thinkingEffort
            .trim()
            .lowercase()
            .takeIf { it in ClaudeCodeAdapter.SUPPORTED_THINKING_EFFORTS }
            ?: ClaudeCodeAdapter.DEFAULT_THINKING_EFFORT

    fun escalatedBudget(current: Int): Int? {
        val cap = tokensCap
        if (current >= cap) return null
        return (current * 2L).coerceAtMost(cap.toLong()).toInt()
    }

    fun isAdaptiveThinkingRejected(message: String?): Boolean =
        message != null &&
            message.contains("ClaudeCode error (HTTP 400)") &&
            message.contains("adaptive thinking is not supported")

    fun markDegraded(model: String): Boolean = degradedModels.add(model)
}
